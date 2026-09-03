package com.alisonsfa.SaltoUrl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alisonsfa.SaltoUrl.domain.entity.Link;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.dto.LinkResponse;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPayload;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPublisher;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class LinkService {
    
    private final LinkRepository linkRepository;
    private final ClickEventPublisher clickEventPublisher;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public LinkService(LinkRepository linkRepository, UserRepository userRepository, ClickEventPublisher clickEventPublisher) {
        this.linkRepository = linkRepository;
        this.clickEventPublisher = clickEventPublisher;
        this.userRepository = userRepository;
    }

    public Optional<String> processRedirect(String code, String rawIp, String userAgent) {
        return linkRepository.findByCodeAndActiveTrue(code)
                .map(link -> {
                    String ipHash = hashIp(rawIp);

                    ClickEventPayload payload = new ClickEventPayload(link.getId(), ipHash, userAgent, null);
                    clickEventPublisher.publishClick(payload);

                    return link.getOriginalUrl();
                });
    }

    public LinkResponse createLink(String originalUrl, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado com o ID: " + userId));
        
                String code = generateUniqueCode();

                Link link = new Link();
                link.setOriginalUrl(originalUrl);
                link.setCode(code);
                link.setUser(user);

                Link savedLink = linkRepository.save(link);

                String shortUrl = "http://localhost:8080/" + savedLink.getCode();

                return new LinkResponse(
                    savedLink.getCode(),
                    savedLink.getOriginalUrl(),
                    shortUrl,
                    savedLink.getCreatedAt()
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
        } while (linkRepository.findByCodeAndActiveTrue(newCode).isPresent());

        return newCode;
    }

}
