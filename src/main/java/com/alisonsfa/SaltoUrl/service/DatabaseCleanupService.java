package com.alisonsfa.SaltoUrl.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.alisonsfa.SaltoUrl.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class DatabaseCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final LinkRepository linkRepository;

    public DatabaseCleanupService(RefreshTokenRepository refreshTokenRepository, LinkRepository linkRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.linkRepository = linkRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional 
    public void runDailyCleanup() {
        log.info("Iniciando rotina agendada de limpeza de banco de dados...");

        LocalDateTime now = LocalDateTime.now();

        int deletedTokens = refreshTokenRepository.deleteExpiredOrRevokedTokens(now);
        log.info("Limpeza concluída: {} refresh tokens expirados/revogados foram excluídos.", deletedTokens);

        int deactivatedLinks = linkRepository.deactivateExpiredLinks(now);
        log.info("Limpeza concluida: {} links expirados foram desativados.", deactivatedLinks);
    }
    
}
