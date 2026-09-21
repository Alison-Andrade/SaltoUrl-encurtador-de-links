package com.alisonsfa.SaltoUrl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.dto.LinkResponse;
import com.alisonsfa.SaltoUrl.dto.LinkStatsResponse;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPayload;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPublisher;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class LinkService {
    
    private final LinkRepository linkRepository;
    private final ClickEventPublisher clickEventPublisher;
    private final UserRepository userRepository;
    private final ClickEventRepository clickEventRepository;

    @Value("${base.url}")
    private String baseUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    public LinkService(LinkRepository linkRepository, UserRepository userRepository, ClickEventPublisher clickEventPublisher, ClickEventRepository clickEventRepository) {
        this.linkRepository = linkRepository;
        this.clickEventPublisher = clickEventPublisher;
        this.userRepository = userRepository;
        this.clickEventRepository = clickEventRepository;
    }

    public Optional<String> processRedirect(String code, String rawIp, String userAgent) {
        return linkRepository.findByCodeAndActiveTrue(code)
                .filter(link -> link.getExpiresAt() == null || link.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(link -> {
                    String ipHash = hashIp(rawIp);

                    ClickEventPayload payload = new ClickEventPayload(link.getId(), ipHash, userAgent, null);
                    clickEventPublisher.publishClick(payload);

                    return link.getOriginalUrl();
                });
    }

    public LinkResponse createLink(String originalUrl, LocalDateTime expiresAt, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado ou inexistente"));
        
                String code = generateUniqueCode();

                Link link = new Link();
                link.setOriginalUrl(originalUrl);
                link.setCode(code);
                link.setUser(user);
                link.setExpiresAt(expiresAt);

                Link savedLink = linkRepository.save(link);

                String shortUrl = baseUrl + savedLink.getCode();

                return new LinkResponse(
                    savedLink.getCode(),
                    savedLink.getOriginalUrl(),
                    shortUrl,
                    savedLink.getCreatedAt(),
                    savedLink.getExpiresAt()
                );
    }

    private String hashIp(String ip) {
        if (ip == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Algoritmo de hash não encontrado: {}", e.getMessage(), e);
            return "hash_error";
        }
    }

    private String generateUniqueCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder codeBuilder = new StringBuilder();

        String newCode;

        do {
            codeBuilder.setLength(0);
            for (int i = 0; i < 6; i++) {
                int index = secureRandom.nextInt(characters.length());
                codeBuilder.append(characters.charAt(index));
            }
            newCode = codeBuilder.toString();
        } while (linkRepository.findByCode(newCode).isPresent());

        return newCode;
    }

    public LinkStatsResponse getLinkStats(String code, UUID userId) {
        Link link = linkRepository.findByCodeAndUserId(code, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link não encontrado para este usuário"));
            
        long totalClicks = clickEventRepository.countByLinkId(link.getId());
        
        List<LinkStatsResponse.RecentClickDto> recentClicks = clickEventRepository.findTop10ByLinkIdOrderByClickedAtDesc(link.getId())
                .stream()
                .map(click -> new LinkStatsResponse.RecentClickDto(click.getClickedAt(), click.getUserAgent(), click.getCountry()))
                .toList();
        
        String shortUrl = baseUrl + "/" + link.getCode();
        
        return new LinkStatsResponse(
            link.getCode(),
            link.getOriginalUrl(),
            shortUrl,
            link.isActive(),
            totalClicks,
            link.getCreatedAt(),
            link.getExpiresAt(),
            recentClicks
        );
    }

    public Page<LinkResponse> getUserLinks(UUID userId, Pageable pageable) {
        return linkRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(link -> new LinkResponse(
                        link.getCode(), 
                        link.getOriginalUrl(), 
                        baseUrl + "/" + link.getCode(), 
                        link.getCreatedAt(),
                        link.getExpiresAt()
                ));
    }

    public void deactivateLink(String code, UUID userId) {
        Link link = linkRepository.findByCodeAndUserId(code, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link não encontrado para este usuário"));

        link.setActive(false);
        linkRepository.save(link);
    }

}
