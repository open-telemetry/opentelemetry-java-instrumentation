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

  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

  // Stable semconv keys
  static final AttributeKey<String> RPC_SYSTEM_NAME = AttributeKey.stringKey("rpc.system.name");

  // removed in stable semconv (merged into rpc.method)
  static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");

  // use RPC_SYSTEM_NAME for stable semconv
  static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

  static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");

  private final RpcAttributesGetter<REQUEST> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @SuppressWarnings("deprecation") // for getMethod()
  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    String system = getter.getSystem(request);

    if (SemconvStability.emitStableRpcSemconv()) {
      internalSet(
          attributes,
          RPC_SYSTEM_NAME,
          system == null ? null : SemconvStability.stableRpcSystemName(system));
      String method = getter.getRpcMethod(request);
      if (getter.isWellKnownMethod(request)) {
        internalSet(attributes, RPC_METHOD, method);
      } else {
        internalSet(attributes, RPC_METHOD_ORIGINAL, method);
        internalSet(attributes, RPC_METHOD, "_OTHER");
      }
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      internalSet(attributes, RPC_SYSTEM, system);
      internalSet(attributes, RPC_SERVICE, getter.getService(request));
      internalSet(
          attributes, SemconvStability.getOldRpcMethodAttributeKey(), getter.getMethod(request));
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
