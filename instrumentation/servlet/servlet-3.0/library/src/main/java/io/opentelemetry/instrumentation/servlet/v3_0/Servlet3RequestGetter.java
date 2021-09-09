/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

public class Servlet3RequestGetter
    implements TextMapGetter<ServletRequestContext<HttpServletRequest>> {

  public static final Servlet3RequestGetter GETTER = new Servlet3RequestGetter();

  @Override
  public Iterable<String> keys(ServletRequestContext<HttpServletRequest> carrier) {
    return Collections.list(carrier.request().getHeaderNames());
  }

  @Override
  public String get(ServletRequestContext<HttpServletRequest> carrier, String key) {
    return carrier.request().getHeader(key);
  }
}
