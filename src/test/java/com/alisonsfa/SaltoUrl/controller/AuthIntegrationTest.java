package com.alisonsfa.SaltoUrl.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.alisonsfa.SaltoUrl.domain.entity.RefreshToken;
import com.alisonsfa.SaltoUrl.dto.LoginRequest;
import com.alisonsfa.SaltoUrl.dto.RegisterRequest;
import com.alisonsfa.SaltoUrl.repository.RefreshTokenRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve cadastrar um usuário com sucesso")
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest("novo.usuario@email.com", "senha123456");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail("novo.usuario@email.com")).isPresent();
    }

    @Test
    @DisplayName("Deve retornar 409 Conflict ao tentar cadastrar email já existente")
    void shouldReturnConflictWhenRegisteringDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicado@email.com", "senha123456");

        // Primeiro cadastro
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Tentativa com mesmo email
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email já cadastrado"));
    }

    @Test
    @DisplayName("Deve logar com sucesso e definir os cookies HttpOnly de JWT e RefreshToken")
    void shouldLoginSuccessfullyAndSetCookies() throws Exception {
        // Cadastra o usuário primeiro
        RegisterRequest registerRequest = new RegisterRequest("login.test@email.com", "senhaSegura123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Executa o login
        LoginRequest loginRequest = new LoginRequest("login.test@email.com", "senhaSegura123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login realizado com sucesso"))
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));

        // Verifica se o refresh token foi persistido no banco de dados
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Deve retornar 401 Unauthorized ao tentar logar com senha incorreta")
    void shouldFailLoginWithInvalidPassword() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("senha.errada@email.com", "senhaCorreta123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("senha.errada@email.com", "senhaErrada999");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos"));
    }

    @Test
    @DisplayName("Deve rotacionar o refresh token e emitir novo JWT ao chamar /auth/refresh")
    void shouldRotateTokensOnRefresh() throws Exception {
        // 1. Cadastra e faz login para obter os cookies iniciais
        RegisterRequest registerRequest = new RegisterRequest("refresh.test@email.com", "senhaForte123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("refresh.test@email.com", "senhaForte123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie initialRefreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(initialRefreshTokenCookie).isNotNull();

        // 2. Chama /auth/refresh enviando o cookie
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                .cookie(initialRefreshTokenCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().exists("refreshToken"))
                .andReturn();

        Cookie newRefreshTokenCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertThat(newRefreshTokenCookie).isNotNull();
        // Garante que o novo token gerado é diferente do anterior (Rotação de Token)
        assertThat(newRefreshTokenCookie.getValue()).isNotEqualTo(initialRefreshTokenCookie.getValue());
    }

    @Test
    @DisplayName("Deve retornar 401 ao chamar /auth/refresh sem o cookie de refresh token")
    void shouldFailRefreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token não encontrado"));
    }

    @Test
    @DisplayName("Deve revogar o refresh token no banco e limpar os cookies no logout")
    void shouldLogoutAndRevokeRefreshToken() throws Exception {
        // 1. Cadastra e faz login
        RegisterRequest registerRequest = new RegisterRequest("logout.test@email.com", "senhaSegura123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("logout.test@email.com", "senhaSegura123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshTokenCookie).isNotNull();

        // 2. Executa o logout
        mockMvc.perform(post("/auth/logout")
                .cookie(refreshTokenCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().maxAge("refreshToken", 0));

        // 3. Verifica no banco se o token foi marcado como revogado
        RefreshToken tokenNoBanco = refreshTokenRepository.findAll().get(0);
        assertThat(tokenNoBanco.isRevoked()).isTrue();
    }
}
