/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Adapts user-supplied {@link AttributesExtractor} that uses v4.1 {@link
 * io.opentelemetry.instrumentation.netty.v4_1.NettyRequest} to work with internal code that uses
 * common {@link io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class AttributesExtractorAdapter
    implements AttributesExtractor<
        io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest, HttpResponse> {

  private final AttributesExtractor<
          io.opentelemetry.instrumentation.netty.v4_1.NettyRequest, HttpResponse>
      delegate;

  public AttributesExtractorAdapter(
      AttributesExtractor<io.opentelemetry.instrumentation.netty.v4_1.NettyRequest, HttpResponse>
          delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest commonRequest) {
    delegate.onStart(
        attributes,
        parentContext,
        io.opentelemetry.instrumentation.netty.v4_1.NettyRequest.create(commonRequest));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest commonRequest,
      @Nullable HttpResponse response,
      @Nullable Throwable error) {
    delegate.onEnd(
        attributes,
        context,
        io.opentelemetry.instrumentation.netty.v4_1.NettyRequest.create(commonRequest),
        response,
        error);
  }
}
