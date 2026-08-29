package com.alisonsfa.SaltoUrl.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.alisonsfa.SaltoUrl.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ClickEventPublisher {
    
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ClickEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishClick(ClickEventPayload payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                jsonPayload
            );

            log.debug("Evento de clique enviado para a fila. LinkID: {}", payload.linkId());

        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar evento de clique para o LinkID {}: {}", payload.linkId(), e.getMessage(), e);
        }
    }

}
