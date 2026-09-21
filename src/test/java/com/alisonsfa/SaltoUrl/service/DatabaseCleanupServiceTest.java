package com.alisonsfa.SaltoUrl.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.RefreshToken;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.RefreshTokenRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

@SpringBootTest
@Testcontainers
class DatabaseCleanupServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired
    private DatabaseCleanupService cleanupService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
        refreshTokenRepository.deleteAll();
        linkRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("cleanup.user@email.com");
        user.setPasswordHash("senha123");
        defaultUser = userRepository.save(user);
    }

    @Test
    @DisplayName("Deve excluir refresh tokens expirados e revogados, mantendo apenas os válidos")
    void shouldDeleteExpiredOrRevokedRefreshTokens() {
        // 1. Token válido (expira em 5 dias, revoked = false)
        createRefreshToken("hash-valido", LocalDateTime.now().plusDays(5), false);

        // 2. Token expirado (venceu ontem, revoked = false)
        createRefreshToken("hash-expirado", LocalDateTime.now().minusDays(1), false);

        // 3. Token revogado (expira no futuro, mas revoked = true)
        createRefreshToken("hash-revogado", LocalDateTime.now().plusDays(5), true);

        assertThat(refreshTokenRepository.findAll()).hasSize(3);

        // Act: Executa a rotina agendada
        cleanupService.runDailyCleanup();

        // Assert: Apenas o token válido deve restar no banco
        var tokensRestantes = refreshTokenRepository.findAll();
        assertThat(tokensRestantes).hasSize(1);
        assertThat(tokensRestantes.get(0).getTokenHash()).isEqualTo("hash-valido");
        assertThat(tokensRestantes.get(0).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Deve desativar links vencidos, mantendo ativos os links válidos e permanentes")
    void shouldDeactivateExpiredLinks() {
        // 1. Link permanente (sem data de expiração, ativo)
        Link permanente = createLink("perm12", "https://permanente.com", null, true);

        // 2. Link válido no futuro (expira em 3 dias, ativo)
        Link futuro = createLink("fut123", "https://futuro.com", LocalDateTime.now().plusDays(3), true);

        // 3. Link expirado no passado (expirou ontem, ativo)
        Link expirado = createLink("exp123", "https://expirado.com", LocalDateTime.now().minusDays(1), true);

        // Act: Executa a limpeza
        cleanupService.runDailyCleanup();

        // Assert
        Link permAposLimpeza = linkRepository.findById(permanente.getId()).orElseThrow();
        assertThat(permAposLimpeza.isActive()).isTrue();

        Link futAposLimpeza = linkRepository.findById(futuro.getId()).orElseThrow();
        assertThat(futAposLimpeza.isActive()).isTrue();

        Link expAposLimpeza = linkRepository.findById(expirado.getId()).orElseThrow();
        assertThat(expAposLimpeza.isActive()).isFalse(); // <-- Deve ter sido desativado!
    }

    private RefreshToken createRefreshToken(String tokenHash, LocalDateTime expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken();
        token.setUser(defaultUser);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        return refreshTokenRepository.save(token);
    }

    private Link createLink(String code, String originalUrl, LocalDateTime expiresAt, boolean active) {
        Link link = new Link();
        link.setCode(code);
        link.setOriginalUrl(originalUrl);
        link.setUser(defaultUser);
        link.setExpiresAt(expiresAt);
        link.setActive(active);
        return linkRepository.save(link);
    }
}

