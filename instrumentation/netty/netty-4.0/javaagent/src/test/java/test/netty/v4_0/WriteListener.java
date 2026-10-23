/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.netty.v4_0;

import io.netty.channel.ChannelFutureListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.List;

public final class WriteListener {
  // Application callbacks must not use the javaagent package, which is excluded from wrapping.
  public static ChannelFutureListener capture(List<SpanContext> contexts) {
    return future -> contexts.add(Span.current().getSpanContext());
  }

  private WriteListener() {}
}
