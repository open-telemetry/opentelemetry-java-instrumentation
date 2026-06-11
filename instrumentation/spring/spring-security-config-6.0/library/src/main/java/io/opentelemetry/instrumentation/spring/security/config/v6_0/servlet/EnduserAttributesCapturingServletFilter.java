/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import jakarta.servlet.Filter;

/**
 * A servlet {@link Filter} that captures identity semantic attributes.
 *
 * @deprecated Use {@link UserAttributesCapturingServletFilter} instead.
 */
@Deprecated
public final class EnduserAttributesCapturingServletFilter
    extends UserAttributesCapturingServletFilter {

  public EnduserAttributesCapturingServletFilter(EnduserAttributesCapturer capturer) {
    super(capturer);
  }
}
