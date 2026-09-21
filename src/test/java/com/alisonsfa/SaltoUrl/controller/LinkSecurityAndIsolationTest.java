package com.alisonsfa.SaltoUrl.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.alisonsfa.SaltoUrl.config.security.JwtService;
import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.dto.LinkCreateRequest;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LinkSecurityAndIsolationTest {

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
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    private User userA;
    private User userB;
    private Cookie cookieUserA;
    private Cookie cookieUserB;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        linkRepository.deleteAll();
        userRepository.deleteAll();

        // Cria Usuário A
        User uA = new User();
        uA.setEmail("usera@email.com");
        uA.setPasswordHash("hashA");
        userA = userRepository.save(uA);
        cookieUserA = new Cookie("jwt", jwtService.generateToken(userA));

        // Cria Usuário B
        User uB = new User();
        uB.setEmail("userb@email.com");
        uB.setPasswordHash("hashB");
        userB = userRepository.save(uB);
        cookieUserB = new Cookie("jwt", jwtService.generateToken(userB));
    }

    @Test
    @DisplayName("Deve bloquear requisições sem autenticação para endpoints protegidos de links")
    void shouldBlockUnauthenticatedRequests() throws Exception {
        LinkCreateRequest request = new LinkCreateRequest("https://google.com", null);

        // Tentativa de criar link sem cookie de autenticação
        mockMvc.perform(post("/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Tentativa de listar links sem autenticação
        mockMvc.perform(get("/links"))
                .andExpect(status().isForbidden());

        // Tentativa de ver estatísticas sem autenticação
        mockMvc.perform(get("/links/abc123/stats"))
                .andExpect(status().isForbidden());

        // Tentativa de desativar link sem autenticação
        mockMvc.perform(delete("/links/abc123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve listar apenas os links do próprio usuário autenticado (Multitenancy)")
    void shouldOnlyListLinksOfAuthenticatedUser() throws Exception {
        // Cria 2 links para Usuário A
        createLink(userA, "linkA1", "https://url-a1.com");
        createLink(userA, "linkA2", "https://url-a2.com");

        // Cria 1 link para Usuário B
        createLink(userB, "linkB1", "https://url-b1.com");

        // Usuário A consulta sua lista: deve ver apenas 2 links
        mockMvc.perform(get("/links")
                .cookie(cookieUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].code").value("linkA2"))
                .andExpect(jsonPath("$.content[1].code").value("linkA1"));

        // Usuário B consulta sua lista: deve ver apenas 1 link
        mockMvc.perform(get("/links")
                .cookie(cookieUserB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("linkB1"));
    }

    @Test
    @DisplayName("Usuário B não deve conseguir ver estatísticas do link do Usuário A (retorna 404)")
    void shouldNotAllowViewingStatsOfAnotherUserLink() throws Exception {
        // Usuário A cria um link
        createLink(userA, "secretoA", "https://confidencial-a.com");

        // Usuário A consegue consultar normalmente
        mockMvc.perform(get("/links/secretoA/stats")
                .cookie(cookieUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("secretoA"))
                .andExpect(jsonPath("$.originalUrl").value("https://confidencial-a.com"));

        // Usuário B tenta consultar as estatísticas do link do Usuário A: deve receber 404
        mockMvc.perform(get("/links/secretoA/stats")
                .cookie(cookieUserB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Link não encontrado para este usuário"));
    }

    @Test
    @DisplayName("Usuário B não deve conseguir desativar o link do Usuário A (retorna 404 e preserva link ativo)")
    void shouldNotAllowDeactivatingAnotherUserLink() throws Exception {
        // Usuário A cria um link ativo
        Link linkA = createLink(userA, "alvoA", "https://alvo-a.com");
        assertThat(linkA.isActive()).isTrue();

        // Usuário B tenta desativar o link do Usuário A: deve receber 404
        mockMvc.perform(delete("/links/alvoA")
                .cookie(cookieUserB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Link não encontrado para este usuário"));

        // Verifica no banco se o link continua ativo
        Link linkVerificado = linkRepository.findByCode("alvoA").orElseThrow();
        assertThat(linkVerificado.isActive()).isTrue();

        // Usuário A desativa seu próprio link com sucesso: retorna 204
        mockMvc.perform(delete("/links/alvoA")
                .cookie(cookieUserA))
                .andExpect(status().isNoContent());

        // Agora sim, o link foi desativado
        Link linkDesativado = linkRepository.findByCode("alvoA").orElseThrow();
        assertThat(linkDesativado.isActive()).isFalse();
    }

    private Link createLink(User user, String code, String originalUrl) {
        Link link = new Link();
        link.setCode(code);
        link.setOriginalUrl(originalUrl);
        link.setUser(user);
        link.setActive(true);
        link.setExpiresAt(LocalDateTime.now().plusDays(10));
        return linkRepository.save(link);
    }
}

