/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.server.http;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

/**
 * Tracer extract adapter for {@link HttpServletRequest}.
 *
 * @author Pavol Loffay
 */
// FIXME:  This code is duplicated in several places.  Extract to a common dependency.
public class HttpServletRequestExtractAdapter implements TextMapGetter<HttpServletRequest> {

  public static final HttpServletRequestExtractAdapter GETTER =
      new HttpServletRequestExtractAdapter();

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
