/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import org.springframework.web.server.WebFilter;

/**
 * A {@link WebFilter} that captures identity semantic attributes.
 *
 * @deprecated Use {@link UserAttributesCapturingWebFilter} instead.
 */
@Deprecated
public final class EnduserAttributesCapturingWebFilter extends UserAttributesCapturingWebFilter {

  public EnduserAttributesCapturingWebFilter(EnduserAttributesCapturer capturer) {
    super(capturer);
  }
}
