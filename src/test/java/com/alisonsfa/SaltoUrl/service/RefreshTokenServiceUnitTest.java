package com.alisonsfa.SaltoUrl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.alisonsfa.SaltoUrl.domain.entity.RefreshToken;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setEmail("token.user@email.com");
    }

    @Test
    @DisplayName("Deve gerar refresh token aleatório, salvar seu hash e associar ao usuário")
    void shouldCreateRefreshTokenSuccessfully() {
        String rawToken = refreshTokenService.createRefreshToken(sampleUser);

        assertThat(rawToken).isNotBlank();

        verify(refreshTokenRepository).deleteByUser(sampleUser);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(sampleUser);
        assertThat(saved.getTokenHash()).isNotBlank().isNotEqualTo(rawToken); // Deve persistir apenas o hash
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Deve retornar o token ao validar token não revogado e não expirado")
    void shouldVerifyTokenWhenValid() {
        RefreshToken token = new RefreshToken();
        token.setUser(sampleUser);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().plusDays(5));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        RefreshToken verified = refreshTokenService.verifyAndRotate("rawToken123");

        assertThat(verified).isEqualTo(token);
        assertThat(verified.getUser()).isEqualTo(sampleUser);
    }

    @Test
    @DisplayName("Deve excluir do banco e lançar 401 Unauthorized quando o refresh token estiver expirado")
    void shouldDeleteAndThrowWhenTokenIsExpired() {
        RefreshToken token = new RefreshToken();
        token.setUser(sampleUser);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expirado

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.verifyAndRotate("rawTokenExpirado"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    @DisplayName("Deve excluir do banco e lançar 401 Unauthorized quando o refresh token estiver revogado")
    void shouldDeleteAndThrowWhenTokenIsRevoked() {
        RefreshToken token = new RefreshToken();
        token.setUser(sampleUser);
        token.setRevoked(true); // Revogado
        token.setExpiresAt(LocalDateTime.now().plusDays(5));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.verifyAndRotate("rawTokenRevogado"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    @DisplayName("Deve marcar o token como revogado (revoked = true) ao chamar revokeToken")
    void shouldRevokeTokenSuccessfully() {
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        refreshTokenService.revokeToken("tokenParaRevogar");

        assertThat(token.isRevoked()).isTrue();
    }
}

