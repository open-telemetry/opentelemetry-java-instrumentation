/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.common;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;

public abstract class GatewayTestApplication {
  @Bean
  public GlobalFilter echoFilter() {
    return (exchange, chain) -> exchange.getResponse().writeWith(exchange.getRequest().getBody());
  }
}
