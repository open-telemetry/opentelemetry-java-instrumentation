/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.function.UnaryOperator;

/**
 * Adapts a user-provided {@link SpanNameExtractor} customizer that works with the public {@link
 * NettyRequest} type to work with the internal common NettyRequest type.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class SpanNameExtractorAdapter {

  private SpanNameExtractorAdapter() {}

  static UnaryOperator<
          SpanNameExtractor<io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest>>
      adapt(UnaryOperator<SpanNameExtractor<NettyRequest>> customizer) {
    return original -> {
      // Wrap the original extractor to expose the v4_1 NettyRequest type
      SpanNameExtractor<NettyRequest> wrappedOriginal =
          request -> original.extract(request.delegate());

      // Apply the user's customizer
      SpanNameExtractor<NettyRequest> customized = customizer.apply(wrappedOriginal);

      // Return an extractor that works with the common v4_0 type
      return commonRequest -> customized.extract(NettyRequest.create(commonRequest));
    };
  }
}
