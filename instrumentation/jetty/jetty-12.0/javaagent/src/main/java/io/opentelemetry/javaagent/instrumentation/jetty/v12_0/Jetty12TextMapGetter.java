/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import io.opentelemetry.instrumentation.api.internal.EnumerationUtil;
import java.util.Iterator;
import org.eclipse.jetty.server.Request;

enum Jetty12TextMapGetter implements ExtendedTextMapGetter<Request> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Request carrier) {
    return carrier.getHeaders().getFieldNamesCollection();
  }

  @Override
  public String get(Request carrier, String key) {
    return carrier.getHeaders().get(key);
  }

  @Override
  public Iterator<String> getAll(Request carrier, String key) {
    return EnumerationUtil.asIterator(carrier.getHeaders().getValues(key));
  }
}
