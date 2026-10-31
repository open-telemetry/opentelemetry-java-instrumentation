/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Describes a single tuple emission. It also acts as the carrier that the context propagator writes
 * the injected headers into; the headers are then copied onto the created {@code TupleImpl} so that
 * they can be extracted again when the tuple is processed by a downstream bolt.
 */
public final class StormEmit {

  @Nullable private final String streamId;
  private final List<Object> values;
  private final Map<String, String> headers = new HashMap<>();

  public StormEmit(@Nullable String streamId, List<Object> values) {
    this.streamId = streamId;
    this.values = values;
  }

  @Nullable
  public String getStreamId() {
    return streamId;
  }

  public List<Object> getValues() {
    return values;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }
}
