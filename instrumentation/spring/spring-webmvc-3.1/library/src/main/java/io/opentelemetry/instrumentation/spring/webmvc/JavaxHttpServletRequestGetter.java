/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

enum JavaxHttpServletRequestGetter implements TextMapGetter<HttpServletRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
