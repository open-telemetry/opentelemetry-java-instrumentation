/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestGetter implements TextMapPropagator.Getter<HttpServletRequest> {

  public static final HttpServletRequestGetter GETTER = new HttpServletRequestGetter();

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
