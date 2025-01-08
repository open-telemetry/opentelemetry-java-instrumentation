/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

// Check https://opentelemetry.io/docs/specs/semconv/rpc/json-rpc/
final class JsonRpcClientAttributesExtractor
    implements AttributesExtractor<SimpleJsonRpcRequest, SimpleJsonRpcResponse> {

  //  private final JsonRpcClientAttributesGetter getter;
  //
  //
  //  JsonRpcClientAttributesExtractor(JsonRpcClientAttributesGetter getter) {
  //    this.getter = getter;
  //  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, SimpleJsonRpcRequest jsonRpcRequest) {
    attributes.put("rpc.jsonrpc.version", "2.0");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SimpleJsonRpcRequest jsonRpcRequest,
      @Nullable SimpleJsonRpcResponse jsonRpcResponse,
      @Nullable Throwable error) {}
}
