/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

  // Stable semconv keys
  static final AttributeKey<String> RPC_SYSTEM_NAME = AttributeKey.stringKey("rpc.system.name");

  // removed in stable semconv (merged into rpc.method)
  static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");

  // use RPC_SYSTEM_NAME for stable semconv
  static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

  private final RpcAttributesGetter<REQUEST, RESPONSE> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @SuppressWarnings("deprecation") // for getSystem(), getMethod()
  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {

    if (emitStableRpcSemconv()) {
      attributes.put(RPC_SYSTEM_NAME, getter.getRpcSystemName(request));
      attributes.put(RPC_METHOD, getter.getRpcMethod(request));
    }

    if (emitOldRpcSemconv()) {
      attributes.put(RPC_SYSTEM, getter.getSystem(request));
      attributes.put(RPC_SERVICE, getter.getService(request));
      if (!emitStableRpcSemconv()) {
        // only set old rpc.method on spans when there's no clash with stable rpc.method
        attributes.put(RPC_METHOD, getter.getMethod(request));
      }
    }
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    if (emitStableRpcSemconv()) {
      String errorType = getter.getErrorType(request, response, error);
      // fall back to exception class name
      if (errorType == null && error != null) {
        errorType = error.getClass().getName();
      }
      attributes.put(ERROR_TYPE, errorType);
    }
  }
}
