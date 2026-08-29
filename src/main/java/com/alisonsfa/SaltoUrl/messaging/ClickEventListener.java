package com.alisonsfa.SaltoUrl.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.alisonsfa.SaltoUrl.config.RabbitMQConfig;
import com.alisonsfa.SaltoUrl.domain.entity.ClickEvent;
import com.alisonsfa.SaltoUrl.repository.ClickEventRepository;
import com.alisonsfa.SaltoUrl.repository.LinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ClickEventListener {
    
    private final ClickEventRepository clickEventRepository;
    private final LinkRepository linkRepository;
    private final ObjectMapper objectMapper;

    public ClickEventListener(ClickEventRepository clickEventRepository, LinkRepository linkRepository, ObjectMapper objectMapper) {
        this.clickEventRepository = clickEventRepository;
        this.linkRepository = linkRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processClickEvent(String jsonMessage) {
        try {
            ClickEventPayload payload = objectMapper.readValue(jsonMessage, ClickEventPayload.class);

            linkRepository.findById(payload.linkId()).ifPresent(link -> {
                ClickEvent event = new ClickEvent();
                event.setLink(link);
                event.setIpHash(payload.ipHash());
                event.setUserAgent(payload.userAgent());
                event.setCountry(payload.country());

                clickEventRepository.save(event);

                log.info("Clique registrado com sucesso para o LinkID: {}", link.getId());
            });
        } catch (Exception e) {
            log.error("Erro ao processar evento de clique: {}", e.getMessage(), e);
        }
    }
 
}
