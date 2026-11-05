package com.stayease.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    @Test
    @DisplayName("Should generate UUID X-Correlation-ID when missing on inbound request")
    void shouldGenerateCorrelationIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            String correlationId = mutatedExchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
            assertNotNull(correlationId, "Correlation ID should be generated");
            assertFalse(correlationId.isBlank(), "Correlation ID should not be blank");
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should propagate existing X-Correlation-ID when present on inbound request")
    void shouldPropagateExistingCorrelationId() {
        String existingId = "custom-test-correlation-id-12345";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels")
                .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            String correlationId = mutatedExchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
            assertEquals(existingId, correlationId, "Existing correlation ID should be preserved");
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(any(ServerWebExchange.class));
    }
}
