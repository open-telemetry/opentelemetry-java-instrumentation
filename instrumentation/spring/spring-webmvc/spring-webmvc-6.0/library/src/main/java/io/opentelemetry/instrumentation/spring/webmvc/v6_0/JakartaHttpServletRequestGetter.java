/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.EnumerationUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Iterator;
import javax.annotation.Nullable;

enum JakartaHttpServletRequestGetter implements TextMapGetter<HttpServletRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  @Nullable
  public String get(@Nullable HttpServletRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpServletRequest carrier, String key) {
    if (carrier == null) {
      return Collections.emptyIterator();
    }
    return EnumerationUtil.asIterator(carrier.getHeaders(key));
  }
}
