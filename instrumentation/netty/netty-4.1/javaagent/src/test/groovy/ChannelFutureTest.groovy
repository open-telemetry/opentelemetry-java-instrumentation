/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.GenericProgressiveFutureListener
import io.netty.util.concurrent.ProgressiveFuture
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ChannelFutureTest extends AgentInstrumentationSpecification {
  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2705
  def "should clean up wrapped listeners"() {
    given:
    def channel = new EmbeddedChannel()
    def counter = new AtomicInteger()

    def listener1 = newListener(counter)
    channel.closeFuture().addListener(listener1)
    channel.closeFuture().removeListener(listener1)

    def listener2 = newListener(counter)
    def listener3 = newProgressiveListener(counter)
    channel.closeFuture().addListeners(listener2, listener3)
    channel.closeFuture().removeListeners(listener2, listener3)

    when:
    channel.close().await(5, TimeUnit.SECONDS)

    then:
    counter.get() == 0
  }

  private static GenericFutureListener newListener(AtomicInteger counter) {
    new GenericFutureListener() {
      void operationComplete(Future future) throws Exception {
        counter.incrementAndGet()
      }
    }
  }

  private static GenericFutureListener newProgressiveListener(AtomicInteger counter) {
    new GenericProgressiveFutureListener() {
      void operationProgressed(ProgressiveFuture future, long progress, long total) throws Exception {
        counter.incrementAndGet()
      }

      void operationComplete(Future future) throws Exception {
        counter.incrementAndGet()
      }
    }
  }
}
