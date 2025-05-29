/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A servlet {@link Filter} that captures {@code endpoint.*} semantic attributes from the {@link
 * org.springframework.security.core.Authentication} in the current {@link
 * org.springframework.security.core.context.SecurityContext} retrieved from {@link
 * SecurityContextHolder}.
 *
 * <p>Inserted into the filter chain by {@link EnduserAttributesHttpSecurityCustomizer} after all
 * the filters that populate the {@link org.springframework.security.core.context.SecurityContext}
 * in the {@link org.springframework.security.core.context.SecurityContextHolder}.
 */
public class EnduserAttributesCapturingServletFilter implements Filter {

  private final EnduserAttributesCapturer capturer;

  public EnduserAttributesCapturingServletFilter(EnduserAttributesCapturer capturer) {
    this.capturer = Objects.requireNonNull(capturer, "capturer must not be null");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    capturer.captureEnduserAttributes(
        Context.current(), SecurityContextHolder.getContext().getAuthentication());

    chain.doFilter(request, response);
  }
}
