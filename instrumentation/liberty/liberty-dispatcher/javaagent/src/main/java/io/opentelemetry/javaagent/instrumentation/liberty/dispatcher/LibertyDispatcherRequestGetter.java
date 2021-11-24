/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.context.propagation.TextMapGetter;

enum LibertyDispatcherRequestGetter implements TextMapGetter<LibertyRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(LibertyRequest carrier) {
    return carrier.getAllHeaderNames();
  }

  @Override
  public String get(LibertyRequest carrier, String key) {
    return carrier.getHeaderValue(key);
  }
}
