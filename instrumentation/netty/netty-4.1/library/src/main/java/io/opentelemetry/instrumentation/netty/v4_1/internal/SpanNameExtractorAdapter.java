/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.netty.v4_1.NettyRequestAccessor;

/**
 * Adapts user-supplied {@link SpanNameExtractor} that uses v4.1 {@link
 * io.opentelemetry.instrumentation.netty.v4_1.NettyRequest} to work with internal code that uses
 * common {@link io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SpanNameExtractorAdapter
    implements SpanNameExtractor<io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest> {

  private final SpanNameExtractor<io.opentelemetry.instrumentation.netty.v4_1.NettyRequest>
      delegate;

  public SpanNameExtractorAdapter(
      SpanNameExtractor<io.opentelemetry.instrumentation.netty.v4_1.NettyRequest> delegate) {
    this.delegate = delegate;
  }

  @Override
  public String extract(io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest request) {
    return delegate.extract(io.opentelemetry.instrumentation.netty.v4_1.NettyRequest.create(request));
  }

  /**
   * Wraps a {@link SpanNameExtractor} that works with common NettyRequest to make it work with
   * v4.1 NettyRequest.
   */
  public static SpanNameExtractor<io.opentelemetry.instrumentation.netty.v4_1.NettyRequest> reverse(
      SpanNameExtractor<io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest>
          extractor) {
    return request -> extractor.extract(NettyRequestAccessor.getDelegate(request));
  }
}
