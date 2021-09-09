/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v2_2;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

public class Servlet2RequestGetter
    implements TextMapGetter<ServletRequestContext<HttpServletRequest>> {

  public static final Servlet2RequestGetter GETTER = new Servlet2RequestGetter();

  @Override
  public Iterable<String> keys(ServletRequestContext<HttpServletRequest> carrier) {
    return Collections.list(carrier.request().getHeaderNames());
  }

  @Override
  public String get(ServletRequestContext<HttpServletRequest> carrier, String key) {
    return carrier.request().getHeader(key);
  }
}
