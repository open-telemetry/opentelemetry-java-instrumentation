/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import io.opentelemetry.instrumentation.api.internal.EnumerationUtil;
import java.util.Collections;
import java.util.Iterator;
import javax.servlet.http.HttpServletRequest;

enum JavaxHttpServletRequestGetter implements ExtendedTextMapGetter<HttpServletRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(HttpServletRequest carrier, String key) {
    return EnumerationUtil.asIterator(carrier.getHeaders(key));
  }
}
