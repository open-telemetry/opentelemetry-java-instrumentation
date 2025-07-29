/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import io.opentelemetry.instrumentation.api.internal.EnumerationUtil;
import java.util.Collections;
import java.util.Iterator;
import org.apache.coyote.Request;

enum TomcatRequestGetter implements ExtendedTextMapGetter<Request> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Request carrier) {
    return Collections.list(carrier.getMimeHeaders().names());
  }

  @Override
  public String get(Request carrier, String key) {
    return carrier.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(Request carrier, String key) {
    return EnumerationUtil.asIterator(carrier.getMimeHeaders().values(key));
  }
}
