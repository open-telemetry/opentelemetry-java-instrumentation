/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.server.http;

import io.opentelemetry.context.propagation.TextMapPropagator;
import javax.servlet.http.HttpServletRequest;

/**
 * Tracer extract adapter for {@link HttpServletRequest}.
 *
 * @author Pavol Loffay
 */
// FIXME:  This code is duplicated in several places.  Extract to a common dependency.
public class HttpServletRequestExtractAdapter
    implements TextMapPropagator.Getter<HttpServletRequest> {

  public static final HttpServletRequestExtractAdapter GETTER =
      new HttpServletRequestExtractAdapter();

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
