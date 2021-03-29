/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

public class JakartaHttpServletRequestGetter implements TextMapGetter<HttpServletRequest> {

  public static final JakartaHttpServletRequestGetter GETTER =
      new JakartaHttpServletRequestGetter();

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
