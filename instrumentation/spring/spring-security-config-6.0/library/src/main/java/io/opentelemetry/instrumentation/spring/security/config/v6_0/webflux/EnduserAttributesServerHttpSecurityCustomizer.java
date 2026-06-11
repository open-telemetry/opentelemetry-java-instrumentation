/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import org.springframework.security.config.web.server.ServerHttpSecurity;

/**
 * Customizes a {@link ServerHttpSecurity} by inserting a filter that captures identity semantic
 * attributes.
 *
 * @deprecated Use {@link UserAttributesServerHttpSecurityCustomizer} instead.
 */
@Deprecated
public final class EnduserAttributesServerHttpSecurityCustomizer
    extends UserAttributesServerHttpSecurityCustomizer {

  public EnduserAttributesServerHttpSecurityCustomizer(EnduserAttributesCapturer capturer) {
    super(capturer);
  }
}
