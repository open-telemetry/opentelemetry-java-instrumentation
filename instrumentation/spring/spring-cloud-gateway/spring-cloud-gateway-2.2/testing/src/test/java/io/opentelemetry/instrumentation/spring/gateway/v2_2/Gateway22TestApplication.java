/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v2_2;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Gateway22TestApplication {
  @Bean
  public GlobalFilter echoFilter() {
    return (exchange, chain) -> exchange.getResponse().writeWith(exchange.getRequest().getBody());
  }
}
