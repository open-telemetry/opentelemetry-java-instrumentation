/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GenericProgressiveFutureListener;
import io.netty.util.concurrent.ProgressiveFuture;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ChannelFutureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2705
  @DisplayName("should clean up wrapped listeners")
  @Test
  void testCleanUpWrappedListeners() throws InterruptedException {
    // given
    EmbeddedChannel channel = new EmbeddedChannel();
    AtomicInteger counter = new AtomicInteger();

    GenericFutureListener listener1 = newListener(counter);
    channel.closeFuture().addListener(listener1);
    channel.closeFuture().removeListener(listener1);

    GenericFutureListener listener2 = newListener(counter);
    GenericFutureListener listener3 = newProgressiveListener(counter);
    channel.closeFuture().addListeners(listener2, listener3);
    channel.closeFuture().removeListeners(listener2, listener3);

    // when
    channel.close().await(5, TimeUnit.SECONDS);

    // then
    assertThat(counter.get()).isEqualTo(0);
  }

  private static GenericFutureListener newListener(AtomicInteger counter) {
    return new GenericFutureListener() {
      @Override
      public void operationComplete(Future future) throws Exception {
        counter.incrementAndGet();
      }
    };
  }

  private static GenericFutureListener newProgressiveListener(AtomicInteger counter) {
    return new GenericProgressiveFutureListener() {
      @Override
      public void operationProgressed(ProgressiveFuture future, long progress, long total)
          throws Exception {
        counter.incrementAndGet();
      }

      @Override
      public void operationComplete(Future future) throws Exception {
        counter.incrementAndGet();
      }
    };
  }
}
