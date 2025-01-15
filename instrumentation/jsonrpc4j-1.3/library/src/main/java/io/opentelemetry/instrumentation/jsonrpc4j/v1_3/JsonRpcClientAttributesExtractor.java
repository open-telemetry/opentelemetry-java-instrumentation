/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

// Check https://opentelemetry.io/docs/specs/semconv/rpc/json-rpc/
final class JsonRpcClientAttributesExtractor
    implements AttributesExtractor<JsonRpcClientRequest, JsonRpcClientResponse> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, JsonRpcClientRequest jsonRpcRequest) {
    attributes.put("rpc.jsonrpc.version", "2.0");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      JsonRpcClientRequest jsonRpcRequest,
      @Nullable JsonRpcClientResponse jsonRpcResponse,
      @Nullable Throwable error) {}
}
