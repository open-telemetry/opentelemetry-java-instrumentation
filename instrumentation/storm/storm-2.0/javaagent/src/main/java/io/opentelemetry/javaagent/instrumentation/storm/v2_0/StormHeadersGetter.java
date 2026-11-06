/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.storm.tuple.TupleImpl;

enum StormHeadersGetter implements TextMapGetter<TupleImpl> {
  INSTANCE;

  @Override
  public Iterable<String> keys(TupleImpl tuple) {
    Map<String, String> headers = VirtualFieldStore.getHeaders(tuple);
    return headers == null ? emptyList() : headers.keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable TupleImpl tuple, String key) {
    if (tuple == null) {
      return null;
    }
    Map<String, String> headers = VirtualFieldStore.getHeaders(tuple);
    return headers == null ? null : headers.get(key);
  }
}
