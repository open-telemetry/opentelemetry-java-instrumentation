/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SpringCloudGatewayRouteFilterTest {
  private final SpringCloudGatewayRouteFilter underTest = new SpringCloudGatewayRouteFilter();

  @Mock private GatewayFilterChain chain;
  @Mock private ServerWebExchange exchange;

  @Test
  void delegatesToFilterChainAfterReadingRoute() {
    // given
    Mono<Void> expected = Mono.empty();
    when(chain.filter(any())).thenReturn(expected);

    // when
    Mono<Void> actual = underTest.filter(exchange, chain);

    // then
    assertThat(actual).isSameAs(expected);
    InOrder callOrder = inOrder(exchange, chain);
    callOrder.verify(exchange).getAttribute(GATEWAY_ROUTE_ATTR);
    callOrder.verify(chain).filter(exchange);
    callOrder.verifyNoMoreInteractions();
  }

  @Test
  void hasHighestPrecedence() {
    assertThat(underTest.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
