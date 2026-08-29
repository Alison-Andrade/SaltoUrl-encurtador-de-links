package com.alisonsfa.SaltoUrl.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.alisonsfa.SaltoUrl.service.LinkService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@RestController 
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String ip = extractIp(request);

        return linkService.processRedirect(code, ip, userAgent)
                .map(originalUrl -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(originalUrl))
                        .<Void>build())
                .orElseGet(() -> {
                    log.warn("Tentativa de acesso a link inexistente ou inativo. Código: {}", code);
                    return ResponseEntity.notFound().build();
                });
    }

    private String extractIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    
}
