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

abstract class RpcCommonAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from RpcIncubatingAttributes
  static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
  static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
  static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
  static final AttributeKey<Long> RPC_CLIENT_REQUEST_BODY_SIZE =
      AttributeKey.longKey("rpc.client.request.body.size");
  static final AttributeKey<Long> RPC_CLIENT_RESPONSE_BODY_SIZE =
      AttributeKey.longKey("rpc.client.response.body.size");
  static final AttributeKey<Long> RPC_SERVER_REQUEST_BODY_SIZE =
      AttributeKey.longKey("rpc.server.request.body.size");
  static final AttributeKey<Long> RPC_SERVER_RESPONSE_BODY_SIZE =
      AttributeKey.longKey("rpc.server.response.body.size");

  private final RpcAttributesGetter<REQUEST> getter;

  RpcCommonAttributesExtractor(RpcAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public final void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, RPC_SYSTEM, getter.getSystem(request));
    internalSet(attributes, RPC_SERVICE, getter.getService(request));
    internalSet(attributes, RPC_METHOD, getter.getMethod(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalSet(
        attributes, RPC_CLIENT_REQUEST_BODY_SIZE, (long) getter.getClientRequestSize(request));
    internalSet(
        attributes, RPC_CLIENT_RESPONSE_BODY_SIZE, (long) getter.getClientResponseSize(request));

    internalSet(
        attributes, RPC_SERVER_REQUEST_BODY_SIZE, (long) getter.getServerRequestSize(request));
    internalSet(
        attributes, RPC_SERVER_RESPONSE_BODY_SIZE, (long) getter.getServerResponseSize(request));
  }
}
