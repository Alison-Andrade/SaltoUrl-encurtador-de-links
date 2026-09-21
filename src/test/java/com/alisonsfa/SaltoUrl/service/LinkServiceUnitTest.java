package com.alisonsfa.SaltoUrl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.dto.LinkResponse;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPayload;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPublisher;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LinkServiceUnitTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @Mock
    private ClickEventRepository clickEventRepository;

    @InjectMocks
    private LinkService linkService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(linkService, "baseUrl", "http://localhost:8080");
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("user@test.com");
    }

    @Test
    @DisplayName("Deve criar link curto com código aleatório de 6 caracteres e prefixo baseUrl correto")
    void shouldCreateLinkSuccessfully() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(linkRepository.findByCode(any())).thenReturn(Optional.empty());
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        LinkResponse response = linkService.createLink("https://spring.io", expiresAt, userId);

        assertThat(response.code()).isNotNull().hasSize(6);
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/" + response.code());
        assertThat(response.originalUrl()).isEqualTo("https://spring.io");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(captor.capture());
        Link saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(sampleUser);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("Deve tentar gerar outro código caso ocorra colisão de código no banco")
    void shouldRetryWhenCodeCollisionOccurs() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        // Simula colisão na primeira tentativa e sucesso na segunda
        when(linkRepository.findByCode(any()))
                .thenReturn(Optional.of(new Link()))
                .thenReturn(Optional.empty());
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LinkResponse response = linkService.createLink("https://github.com", null, userId);

        assertThat(response.code()).hasSize(6);
        // Verifica que o banco foi consultado pelo menos 2 vezes para garantir unicidade
        verify(linkRepository, times(2)).findByCode(any());
    }

    @Test
    @DisplayName("Deve lançar 401 Unauthorized ao tentar criar link para usuário inexistente")
    void shouldThrowUnauthorizedWhenUserDoesNotExist() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.createLink("https://google.com", null, userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");

        verify(linkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve redirecionar link válido e publicar evento de clique com hash do IP")
    void shouldProcessRedirectAndPublishClickEvent() {
        Link link = new Link();
        link.setId(UUID.randomUUID());
        link.setCode("valid1");
        link.setOriginalUrl("https://deepmind.google");
        link.setActive(true);
        link.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(linkRepository.findByCodeAndActiveTrue("valid1")).thenReturn(Optional.of(link));

        Optional<String> result = linkService.processRedirect("valid1", "172.16.0.1", "Mozilla/5.0");

        assertThat(result).isPresent().contains("https://deepmind.google");

        ArgumentCaptor<ClickEventPayload> payloadCaptor = ArgumentCaptor.forClass(ClickEventPayload.class);
        verify(clickEventPublisher).publishClick(payloadCaptor.capture());

        ClickEventPayload payload = payloadCaptor.getValue();
        assertThat(payload.linkId()).isEqualTo(link.getId());
        assertThat(payload.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(payload.ipHash()).isNotNull().isNotEqualTo("172.16.0.1"); // IP deve estar anonimizado
    }

    @Test
    @DisplayName("Não deve redirecionar nem publicar clique quando o link estiver expirado")
    void shouldNotRedirectWhenLinkIsExpired() {
        Link link = new Link();
        link.setCode("exp123");
        link.setOriginalUrl("https://site.com");
        link.setActive(true);
        link.setExpiresAt(LocalDateTime.now().minusMinutes(5)); // Vencido

        when(linkRepository.findByCodeAndActiveTrue("exp123")).thenReturn(Optional.of(link));

        Optional<String> result = linkService.processRedirect("exp123", "127.0.0.1", "curl/7.68.0");

        assertThat(result).isEmpty();
        verify(clickEventPublisher, never()).publishClick(any());
    }

    @Test
    @DisplayName("Deve desativar link (active = false) com sucesso")
    void shouldDeactivateLinkSuccessfully() {
        Link link = new Link();
        link.setCode("deact1");
        link.setActive(true);

        when(linkRepository.findByCodeAndUserId("deact1", userId)).thenReturn(Optional.of(link));

        linkService.deactivateLink("deact1", userId);

        assertThat(link.isActive()).isFalse();
        verify(linkRepository).save(link);
    }
}

