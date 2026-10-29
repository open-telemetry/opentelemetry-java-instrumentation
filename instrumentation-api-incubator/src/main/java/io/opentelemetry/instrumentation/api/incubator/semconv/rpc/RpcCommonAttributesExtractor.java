/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
  private static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");
  private static final AttributeKey<String> RPC_STATUS_CODE =
      AttributeKey.stringKey("rpc.status_code");

  static final AttributeKey<String> RPC_SYSTEM_NAME = AttributeKey.stringKey("rpc.system.name");

  private final RpcAttributesGetter<REQUEST, RESPONSE> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(RPC_SYSTEM_NAME, getter.getRpcSystemName(request));
    attributes.put(RPC_METHOD, getter.getRpcMethod(request));
    attributes.put(RPC_METHOD_ORIGINAL, getter.getRpcMethodOriginal(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    attributes.put(RPC_STATUS_CODE, getter.getRpcStatusCode(request, response, error));
    attributes.put(ERROR_TYPE, getter.getErrorType(request, response, error));
  }
}
