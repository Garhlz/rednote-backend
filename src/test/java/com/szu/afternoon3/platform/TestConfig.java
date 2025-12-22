package com.szu.afternoon3.platform;

import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate mockRabbitTemplate = Mockito.mock(RabbitTemplate.class);
        // Mock void methods to do nothing
        Mockito.doNothing().when(mockRabbitTemplate).convertAndSend(Mockito.anyString(), Mockito.anyString(), (Object) Mockito.any());
        Mockito.doNothing().when(mockRabbitTemplate).convertAndSend(Mockito.anyString(), (Object) Mockito.any());
        return mockRabbitTemplate;
    }
}
