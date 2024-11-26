/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GenericProgressiveFutureListener;
import io.netty.util.concurrent.ProgressiveFuture;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ChannelFutureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @SuppressWarnings("unchecked")
  @Test
  void shouldCleanUpWrappedListeners() throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new EmptyChannelHandler());
    AtomicInteger counter = new AtomicInteger();

    GenericFutureListener<Future<Void>> listener1 = newListener(counter);
    channel.closeFuture().addListener(listener1);
    channel.closeFuture().removeListener(listener1);

    GenericFutureListener<Future<Void>> listener2 = newListener(counter);
    GenericProgressiveFutureListener<ProgressiveFuture<Void>> listener3 =
        newProgressiveListener(counter);
    channel.closeFuture().addListener(listener2);
    channel.closeFuture().addListener(listener3);
    channel.closeFuture().removeListeners(listener2, listener3);

    channel.close().await(5, TimeUnit.SECONDS);

    assertEquals(0, counter.get());
  }

  private static GenericFutureListener<Future<Void>> newListener(AtomicInteger counter) {
    return future -> counter.incrementAndGet();
  }

  private static GenericProgressiveFutureListener<ProgressiveFuture<Void>> newProgressiveListener(
      AtomicInteger counter) {
    return new GenericProgressiveFutureListener<ProgressiveFuture<Void>>() {
      @Override
      public void operationComplete(@NotNull ProgressiveFuture<Void> future) throws Exception {
        counter.incrementAndGet();
      }

      @Override
      public void operationProgressed(ProgressiveFuture<Void> future, long progress, long total)
          throws Exception {
        counter.incrementAndGet();
      }
    };
  }

  private static class EmptyChannelHandler implements ChannelHandler {
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {}

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {}
  }
}
