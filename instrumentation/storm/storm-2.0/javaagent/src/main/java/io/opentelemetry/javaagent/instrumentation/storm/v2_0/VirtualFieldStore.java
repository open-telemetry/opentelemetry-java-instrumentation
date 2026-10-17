/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.storm.tuple.TupleImpl;

/**
 * Stores the propagated context headers on a {@link TupleImpl}. Tuples that stay within a worker
 * keep their identity, which is what makes propagation possible without modifying the tuple
 * payload; tuples that are serialized to another worker lose this state.
 */
public final class VirtualFieldStore {

  private static final VirtualField<TupleImpl, Map<String, String>> TUPLE_HEADERS =
      VirtualField.find(TupleImpl.class, Map.class);

  @Nullable
  public static Map<String, String> getHeaders(TupleImpl tuple) {
    return TUPLE_HEADERS.get(tuple);
  }

  public static void setHeaders(TupleImpl tuple, Map<String, String> headers) {
    TUPLE_HEADERS.set(tuple, headers);
  }

  private VirtualFieldStore() {}
}
