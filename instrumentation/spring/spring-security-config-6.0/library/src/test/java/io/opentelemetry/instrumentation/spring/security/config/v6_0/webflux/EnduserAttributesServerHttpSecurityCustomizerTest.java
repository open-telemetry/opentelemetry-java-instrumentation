/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

class EnduserAttributesServerHttpSecurityCustomizerTest {

  /**
   * Ensures that the {@link EnduserAttributesServerHttpSecurityCustomizer} registers a {@link
   * EnduserAttributesCapturingWebFilter} in the filter chain.
   *
   * <p>Usage of the filter is covered in other unit tests.
   */
  @Test
  void ensureFilterRegistered() {

    ServerHttpSecurity serverHttpSecurity = ServerHttpSecurity.http();

    EnduserAttributesServerHttpSecurityCustomizer customizer =
        new EnduserAttributesServerHttpSecurityCustomizer(new EnduserAttributesCapturer());

    customizer.customize(serverHttpSecurity);

    SecurityWebFilterChain securityWebFilterChain = serverHttpSecurity.build();

    assertThat(securityWebFilterChain.getWebFilters().collectList().block())
        .filteredOn(EnduserAttributesCapturingWebFilter.class::isInstance)
        .hasSize(1);
  }
}
