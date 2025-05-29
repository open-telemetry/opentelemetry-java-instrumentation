/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import java.util.Objects;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;

/**
 * Customizes a {@link ServerHttpSecurity} by inserting a {@link
 * EnduserAttributesCapturingWebFilter} after all the filters that populate the {@link
 * org.springframework.security.core.context.SecurityContext} in the {@link
 * org.springframework.security.core.context.ReactiveSecurityContextHolder}.
 */
public class EnduserAttributesServerHttpSecurityCustomizer
    implements Customizer<ServerHttpSecurity> {

  private final EnduserAttributesCapturer capturer;

  public EnduserAttributesServerHttpSecurityCustomizer(EnduserAttributesCapturer capturer) {
    this.capturer = Objects.requireNonNull(capturer, "capturer must not be null");
  }

  @Override
  public void customize(ServerHttpSecurity serverHttpSecurity) {
    serverHttpSecurity.addFilterBefore(
        new EnduserAttributesCapturingWebFilter(capturer), SecurityWebFiltersOrder.LOGOUT);
  }
}
