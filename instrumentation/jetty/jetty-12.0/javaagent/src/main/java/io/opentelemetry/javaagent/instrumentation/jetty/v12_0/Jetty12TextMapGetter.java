/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.eclipse.jetty.server.Request;

enum Jetty12TextMapGetter implements TextMapGetter<Request> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Request carrier) {
    return carrier.getHeaders().getFieldNamesCollection();
  }

  @Override
  public String get(Request carrier, String key) {
    return carrier.getHeaders().get(key);
  }
}
