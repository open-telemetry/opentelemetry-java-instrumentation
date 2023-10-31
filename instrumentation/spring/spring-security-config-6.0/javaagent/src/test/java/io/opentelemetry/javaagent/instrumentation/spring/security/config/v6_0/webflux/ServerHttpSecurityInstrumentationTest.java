/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux.EnduserAttributesCapturingWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

class ServerHttpSecurityInstrumentationTest {

  /**
   * Ensures that {@link ServerHttpSecurityInstrumentation} registers a {@link
   * EnduserAttributesCapturingWebFilter} in the filter chain.
   *
   * <p>Usage of the filter is covered in other unit tests.
   */
  @Test
  void ensureFilterRegistered() {

    ServerHttpSecurity serverHttpSecurity = ServerHttpSecurity.http();

    SecurityWebFilterChain securityWebFilterChain = serverHttpSecurity.build();

    assertThat(securityWebFilterChain.getWebFilters().collectList().block())
        .filteredOn(
            item ->
                item.getClass()
                    .getName()
                    .endsWith(EnduserAttributesCapturingWebFilter.class.getSimpleName()))
        .hasSize(1);
  }
}
