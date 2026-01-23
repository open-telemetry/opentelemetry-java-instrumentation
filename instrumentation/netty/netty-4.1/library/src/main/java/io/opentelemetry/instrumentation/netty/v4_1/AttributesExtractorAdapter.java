/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Adapts a user-provided {@link AttributesExtractor} that works with the public {@link
 * NettyRequest} type to work with the internal common NettyRequest type.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class AttributesExtractorAdapter
    implements AttributesExtractor<
        io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest, HttpResponse> {

  private final AttributesExtractor<NettyRequest, HttpResponse> delegate;

  AttributesExtractorAdapter(AttributesExtractor<NettyRequest, HttpResponse> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest request) {
    delegate.onStart(attributes, parentContext, NettyRequest.create(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest request,
      @Nullable HttpResponse response,
      @Nullable Throwable error) {
    delegate.onEnd(attributes, context, NettyRequest.create(request), response, error);
  }
}
