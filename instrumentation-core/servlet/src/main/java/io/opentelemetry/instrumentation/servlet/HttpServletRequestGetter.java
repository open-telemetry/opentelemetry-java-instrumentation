/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.context.propagation.TextMapPropagator;
import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestGetter implements TextMapPropagator.Getter<HttpServletRequest> {

  public static final HttpServletRequestGetter GETTER = new HttpServletRequestGetter();

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
