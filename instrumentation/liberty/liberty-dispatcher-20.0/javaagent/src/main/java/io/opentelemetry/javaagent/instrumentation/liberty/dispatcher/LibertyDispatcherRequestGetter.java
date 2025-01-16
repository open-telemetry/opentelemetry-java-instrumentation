/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;

enum LibertyDispatcherRequestGetter implements ExtendedTextMapGetter<LibertyRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(LibertyRequest carrier) {
    return carrier.getAllHeaderNames();
  }

  @Override
  public String get(LibertyRequest carrier, String key) {
    return carrier.getHeaderValue(key);
  }

  @Override
  public Iterator<String> getAll(LibertyRequest carrier, String key) {
    return carrier.getHeaderValues(key).iterator();
  }
}
