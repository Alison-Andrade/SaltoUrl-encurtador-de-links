package com.alisonsfa.SaltoUrl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.alisonsfa.SaltoUrl.messaging.ClickEventPayload;
import com.alisonsfa.SaltoUrl.messaging.ClickEventPublisher;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class LinkService {
    
    private final LinkRepository linkRepository;
    private final ClickEventPublisher clickEventPublisher;

    public LinkService(LinkRepository linkRepository, ClickEventPublisher clickEventPublisher) {
        this.linkRepository = linkRepository;
        this.clickEventPublisher = clickEventPublisher;
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

}
