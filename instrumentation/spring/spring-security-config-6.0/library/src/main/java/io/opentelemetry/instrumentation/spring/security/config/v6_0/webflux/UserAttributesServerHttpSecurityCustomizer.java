/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.UserAttributesCapturer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;

/**
 * Customizes a {@link ServerHttpSecurity} by inserting a {@link UserAttributesCapturingWebFilter}
 * after all the filters that populate the {@link
 * org.springframework.security.core.context.SecurityContext} in the {@link
 * org.springframework.security.core.context.ReactiveSecurityContextHolder}.
 */
public class UserAttributesServerHttpSecurityCustomizer implements Customizer<ServerHttpSecurity> {

  private final UserAttributesCapturer capturer;

  public UserAttributesServerHttpSecurityCustomizer(UserAttributesCapturer capturer) {
    this.capturer = requireNonNull(capturer, "capturer must not be null");
  }

  @Override
  public void customize(ServerHttpSecurity serverHttpSecurity) {
    serverHttpSecurity.addFilterBefore(
        new UserAttributesCapturingWebFilter(capturer), SecurityWebFiltersOrder.LOGOUT);
  }
}
