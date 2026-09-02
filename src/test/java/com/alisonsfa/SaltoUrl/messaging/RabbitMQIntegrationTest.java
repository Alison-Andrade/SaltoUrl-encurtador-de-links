package com.alisonsfa.SaltoUrl.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest // Sobe o contexto inteiro do Spring (diferente do @DataJpaTest)
@Testcontainers 
class RabbitMQIntegrationTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Container 
    @ServiceConnection 
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired 
    private ClickEventPublisher publisher;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private UserRepository userRepository;

    private Link savedLink;

    @BeforeEach 
    void setup() {
        // Limpa a base antes de cada teste
        clickEventRepository.deleteAll();
        linkRepository.deleteAll();
        userRepository.deleteAll();

        // Prepara os dados: Cria um usuário e um link
        User user = new User();
        user.setEmail("async@test.com");
        user.setPasswordHash("hash");
        userRepository.save(user);

        Link link = new Link();
        link.setCode("abc123");
        link.setOriginalUrl("https://github.com/alison");
        link.setUser(user);
        savedLink = linkRepository.save(link);
    }

    @Test 
    void shouldProcessClickEventAndSaveToDatabase() throws InterruptedException {
        // Arrange
        ClickEventPayload payload = new ClickEventPayload(
                savedLink.getId(), 
                "hash-ip-123", 
                "Mozilla/5.0", 
                "BR"
        );

        // Act: Publica a mensagem na fila
        publisher.publishClick(payload);

        // Pausa a thread de teste por 1 segundo (tempo para o RabbitMQ entregar e o Listener gravar no banco)
        // Nota: Em projetos maduros, usamos a biblioteca 'Awaitility' no lugar do Thread.sleep.
        Thread.sleep(1000);

        // Assert: Verifica se o listener consumiu a fila e salvou o evento
        long totalClicks = clickEventRepository.count();
        assertThat(totalClicks).isEqualTo(1);

        var savedEvent = clickEventRepository.findAll().get(0);
        assertThat(savedEvent.getLink().getId()).isEqualTo(savedLink.getId());
        assertThat(savedEvent.getIpHash()).isEqualTo("hash-ip-123");
    }
}