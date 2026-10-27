/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.OpenTelemetryConfigurable;
import javax.annotation.Nullable;

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, OpenTelemetryConfigurable {

  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
  private static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");
  private static final AttributeKey<String> RPC_STATUS_CODE =
      AttributeKey.stringKey("rpc.status_code");

  // Stable semconv keys
  static final AttributeKey<String> RPC_SYSTEM_NAME = AttributeKey.stringKey("rpc.system.name");

  // removed in stable semconv (merged into rpc.method)
  static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");

  // use RPC_SYSTEM_NAME for stable semconv
  static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

  private final RpcAttributesGetter<REQUEST, RESPONSE> getter;
  private final RpcSemconvSelection selection = new RpcSemconvSelection();

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public final void internalInitialize(OpenTelemetry openTelemetry) {
    selection.initialize(openTelemetry);
  }

  @SuppressWarnings("deprecation") // for getSystem(), getMethod()
  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    RpcSemconvSelection.Selection current = selection.get();

    if (current.emitPreview()) {
      attributes.put(RPC_SYSTEM_NAME, getter.getRpcSystemName(request));
      attributes.put(RPC_METHOD, getter.getRpcMethod(request));
      attributes.put(RPC_METHOD_ORIGINAL, getter.getRpcMethodOriginal(request));
    }

    if (current.emitOld()) {
      attributes.put(RPC_SYSTEM, getter.getSystem(request));
      attributes.put(RPC_SERVICE, getter.getService(request));
      if (!current.emitPreview()) {
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
    if (selection.get().emitPreview()) {
      attributes.put(RPC_STATUS_CODE, getter.getRpcStatusCode(request, response, error));
      attributes.put(ERROR_TYPE, getter.getErrorType(request, response, error));
    }
  }
}
