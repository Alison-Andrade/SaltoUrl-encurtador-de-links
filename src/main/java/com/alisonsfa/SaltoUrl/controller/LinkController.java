package com.alisonsfa.SaltoUrl.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alisonsfa.SaltoUrl.dto.LinkCreateRequest;
import com.alisonsfa.SaltoUrl.dto.LinkResponse;
import com.alisonsfa.SaltoUrl.service.LinkService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ResponseStatus;


@Slf4j 
@RestController 
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/{code}")
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

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse create(@RequestBody @Valid LinkCreateRequest request) {
        UUID fakeUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        LinkResponse response = linkService.createLink(request.originalUrl(), fakeUserId);

        return response;
    }

    private String extractIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    
}
