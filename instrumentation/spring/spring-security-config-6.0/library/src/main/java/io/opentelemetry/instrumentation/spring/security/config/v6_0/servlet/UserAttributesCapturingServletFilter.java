/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.UserAttributesCapturer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A servlet {@link Filter} that captures identity semantic attributes from the {@link
 * org.springframework.security.core.Authentication} in the current {@link
 * org.springframework.security.core.context.SecurityContext} retrieved from {@link
 * SecurityContextHolder}.
 *
 * <p>Inserted into the filter chain by {@link UserAttributesHttpSecurityCustomizer} after all the
 * filters that populate the {@link org.springframework.security.core.context.SecurityContext} in
 * the {@link org.springframework.security.core.context.SecurityContextHolder}.
 */
public class UserAttributesCapturingServletFilter implements Filter {

  private final UserAttributesCapturer capturer;

  public UserAttributesCapturingServletFilter(UserAttributesCapturer capturer) {
    this.capturer = requireNonNull(capturer, "capturer must not be null");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    capturer.captureUserAttributes(
        Context.current(), SecurityContextHolder.getContext().getAuthentication());

    chain.doFilter(request, response);
  }
}
