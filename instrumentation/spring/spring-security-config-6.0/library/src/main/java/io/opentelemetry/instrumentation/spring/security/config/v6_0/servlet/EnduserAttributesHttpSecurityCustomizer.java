/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Customizes a {@link HttpSecurity} by inserting a filter that captures identity semantic
 * attributes.
 *
 * @deprecated Use {@link UserAttributesHttpSecurityCustomizer} instead.
 */
@Deprecated
public final class EnduserAttributesHttpSecurityCustomizer
    extends UserAttributesHttpSecurityCustomizer {

  public EnduserAttributesHttpSecurityCustomizer(EnduserAttributesCapturer capturer) {
    super(capturer);
  }
}
