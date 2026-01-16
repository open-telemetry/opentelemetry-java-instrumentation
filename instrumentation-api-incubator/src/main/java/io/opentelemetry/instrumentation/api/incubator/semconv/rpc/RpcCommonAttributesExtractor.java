/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // Stable semconv keys
  static final AttributeKey<String> RPC_SYSTEM_NAME = AttributeKey.stringKey("rpc.system.name");
  static final AttributeKey<String> RPC_METHOD_STABLE = AttributeKey.stringKey("rpc.method");
  static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");

  // copied from RpcIncubatingAttributes
  @Deprecated // use RPC_SYSTEM_NAME for stable semconv
  static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

  @Deprecated // use RPC_METHOD_STABLE for stable semconv
  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

  @Deprecated // removed in stable semconv (merged into rpc.method)
  static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");

  private final RpcAttributesGetter<REQUEST> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @SuppressWarnings("deprecation") // until old rpc semconv are dropped
  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    String system = getter.getSystem(request);

    if (SemconvStability.emitStableRpcSemconv()) {
      internalSet(
          attributes,
          RPC_SYSTEM_NAME,
          system == null ? null : SemconvStability.stableRpcSystemName(system));
      internalSet(attributes, RPC_METHOD_STABLE, getter.getFullMethod(request));
      internalSet(attributes, RPC_METHOD_ORIGINAL, getter.getMethodOriginal(request));
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      internalSet(attributes, RPC_SYSTEM, system);
      internalSet(attributes, RPC_SERVICE, getter.getService(request));
      // In dup mode, stable rpc.method takes precedence over old rpc.method
      // (they use the same key but with different formats)
      if (!SemconvStability.emitStableRpcSemconv()) {
        internalSet(attributes, RPC_METHOD, getter.getMethod(request));
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
    if (SemconvStability.emitStableRpcSemconv()) {
      if (error != null) {
        internalSet(attributes, ERROR_TYPE, error.getClass().getName());
      }
    }
  }
}
