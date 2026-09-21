package com.alisonsfa.SaltoUrl.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LinkRedirectIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    private User defaultUser;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        linkRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("owner@email.com");
        user.setPasswordHash("senha123");
        defaultUser = userRepository.save(user);
    }

    @Test
    @DisplayName("Deve redirecionar (302 Found) com header Location para URL original quando o link estiver ativo e sem expiração")
    void shouldRedirectSuccessfullyWhenLinkIsActiveAndHasNoExpiration() throws Exception {
        Link link = new Link();
        link.setCode("abc123");
        link.setOriginalUrl("https://spring.io");
        link.setUser(defaultUser);
        link.setActive(true);
        link.setExpiresAt(null);
        linkRepository.save(link);

        mockMvc.perform(get("/abc123")
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://spring.io"));
    }

    @Test
    @DisplayName("Deve redirecionar com sucesso quando o link tiver data de expiração no futuro")
    void shouldRedirectSuccessfullyWhenLinkHasFutureExpiration() throws Exception {
        Link link = new Link();
        link.setCode("futur1");
        link.setOriginalUrl("https://github.com");
        link.setUser(defaultUser);
        link.setActive(true);
        link.setExpiresAt(LocalDateTime.now().plusDays(5));
        linkRepository.save(link);

        mockMvc.perform(get("/futur1"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://github.com"));
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found quando o código do link não existir no banco")
    void shouldReturnNotFoundWhenLinkDoesNotExist() throws Exception {
        mockMvc.perform(get("/naoExiste"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found quando o link estiver desativado (active = false)")
    void shouldReturnNotFoundWhenLinkIsInactive() throws Exception {
        Link link = new Link();
        link.setCode("inativo");
        link.setOriginalUrl("https://exemplo.com");
        link.setUser(defaultUser);
        link.setActive(false);
        linkRepository.save(link);

        mockMvc.perform(get("/inativo"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found quando o link estiver expirado (expiresAt no passado)")
    void shouldReturnNotFoundWhenLinkIsExpired() throws Exception {
        Link link = new Link();
        link.setCode("expira");
        link.setOriginalUrl("https://exemplo.com/antigo");
        link.setUser(defaultUser);
        link.setActive(true);
        link.setExpiresAt(LocalDateTime.now().minusMinutes(10)); // Expirou há 10 minutos
        linkRepository.save(link);

        mockMvc.perform(get("/expira"))
                .andExpect(status().isNotFound());
    }
}

