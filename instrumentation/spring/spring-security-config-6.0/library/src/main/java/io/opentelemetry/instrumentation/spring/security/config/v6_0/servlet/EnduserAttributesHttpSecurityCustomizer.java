/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import java.util.Objects;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Customizes a {@link HttpSecurity} by inserting a {@link EnduserAttributesCapturingServletFilter}
 * after all the filters that populate the {@link
 * org.springframework.security.core.context.SecurityContext} in the {@link
 * org.springframework.security.core.context.SecurityContextHolder}.
 */
public class EnduserAttributesHttpSecurityCustomizer implements Customizer<HttpSecurity> {

  private final EnduserAttributesCapturer capturer;

  public EnduserAttributesHttpSecurityCustomizer(EnduserAttributesCapturer capturer) {
    this.capturer = Objects.requireNonNull(capturer, "capturer must not be null");
  }

  @Override
  public void customize(HttpSecurity httpSecurity) {
    /*
     * See org.springframework.security.config.annotation.web.builders.FilterOrderRegistration
     * for where this appears in the chain.
     */
    httpSecurity.addFilterBefore(
        new EnduserAttributesCapturingServletFilter(capturer), AuthorizationFilter.class);
  }
}
