/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

public final class RpcSizeAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  static final AttributeKey<Long> RPC_REQUEST_SIZE = AttributeKey.longKey("rpc.request.size");
  static final AttributeKey<Long> RPC_RESPONSE_SIZE = AttributeKey.longKey("rpc.response.size");

  private final RpcAttributesGetter<REQUEST> getter;

  RpcSizeAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  /**
   * Returns a new {@link RpcSizeAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the request and response size.
   */
  public static <REQUEST, RESPONSE> RpcSizeAttributesExtractor<REQUEST, RESPONSE> create(
      RpcAttributesGetter<REQUEST> attributesGetter) {
    return new RpcSizeAttributesExtractor<>(attributesGetter);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalSet(attributes, RPC_REQUEST_SIZE, getter.getRequestSize(request));
    internalSet(attributes, RPC_RESPONSE_SIZE, getter.getResponseSize(request));
  }
}
