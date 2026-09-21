package com.alisonsfa.SaltoUrl.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.dto.LinkCreateRequest;
import com.alisonsfa.SaltoUrl.dto.LinkResponse;
import com.alisonsfa.SaltoUrl.dto.LinkStatsResponse;
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
    public LinkResponse create(@AuthenticationPrincipal User user,@RequestBody @Valid LinkCreateRequest request) {
        LinkResponse response = linkService.createLink(request.originalUrl(), request.expiresAt(), user.getId());
        return response;
    }

    private String extractIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    
    @GetMapping("/links")
    public Page<LinkResponse> listLinks(
            @AuthenticationPrincipal User user, 
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return linkService.getUserLinks(user.getId(), pageable);
    }

    @GetMapping("/links/{code}/stats")
    public LinkStatsResponse getLinkStats(
            @AuthenticationPrincipal User user, 
            @PathVariable String code
    ) {
        return linkService.getLinkStats(code, user.getId());
    }

    @DeleteMapping("/links/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @AuthenticationPrincipal User user, 
            @PathVariable String code
    ) {
        linkService.deactivateLink(code, user.getId());
    }

}
