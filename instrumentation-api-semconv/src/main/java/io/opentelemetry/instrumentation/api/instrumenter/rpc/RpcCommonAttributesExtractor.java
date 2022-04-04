/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.setAttr;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final RpcAttributesGetter<REQUEST> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    setAttr(attributes, SemanticAttributes.RPC_SYSTEM, getter.system(request));
    setAttr(attributes, SemanticAttributes.RPC_SERVICE, getter.service(request));
    setAttr(attributes, SemanticAttributes.RPC_METHOD, getter.method(request));
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
