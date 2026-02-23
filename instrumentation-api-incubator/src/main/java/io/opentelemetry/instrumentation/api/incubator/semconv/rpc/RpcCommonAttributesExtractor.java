/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from RpcIncubatingAttributes
  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
  static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
  static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

  private final RpcAttributesGetter<REQUEST, RESPONSE> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @SuppressWarnings("deprecation") // for getMethod()
  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(RPC_SYSTEM, getter.getSystem(request));
    attributes.put(RPC_SERVICE, getter.getService(request));
    attributes.put(RPC_METHOD, getter.getMethod(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    // No response attributes
  }
}
