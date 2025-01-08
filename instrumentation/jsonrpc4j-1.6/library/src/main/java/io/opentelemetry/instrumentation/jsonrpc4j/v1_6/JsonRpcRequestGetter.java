/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.ArrayList;
import javax.annotation.Nullable;

enum JsonRpcRequestGetter implements TextMapGetter<JsonRpcRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(JsonRpcRequest request) {
    return new ArrayList<>();
  }

  @Override
  @Nullable
  public String get(@Nullable JsonRpcRequest request, String key) {
    return null;
  }
}
