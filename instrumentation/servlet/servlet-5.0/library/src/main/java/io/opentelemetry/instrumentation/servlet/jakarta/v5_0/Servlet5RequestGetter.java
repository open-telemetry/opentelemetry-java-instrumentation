/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

public class Servlet5RequestGetter
    implements TextMapGetter<ServletRequestContext<HttpServletRequest>> {

  public static final Servlet5RequestGetter GETTER = new Servlet5RequestGetter();

  @Override
  public Iterable<String> keys(ServletRequestContext<HttpServletRequest> carrier) {
    return Collections.list(carrier.request().getHeaderNames());
  }

  @Override
  public String get(ServletRequestContext<HttpServletRequest> carrier, String key) {
    return carrier.request().getHeader(key);
  }
}
