/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import java.lang.ref.WeakReference;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class VirtualFieldHelperTest {

  @Test
  void clearsAssociation() {
    ChannelHandler handler = new ChannelHandlerAdapter() {};
    ReferencingHandler instrumentationHandler = new ReferencingHandler(handler);
    VirtualFieldHelper.setInstrumentationHandler(handler, instrumentationHandler);

    assertThat(VirtualFieldHelper.getInstrumentationHandler(handler))
        .isSameAs(instrumentationHandler);
    assertThat(instrumentationHandler.handler).isSameAs(handler);

    VirtualFieldHelper.setInstrumentationHandler(handler, null);
    assertThat(VirtualFieldHelper.getInstrumentationHandler(handler)).isNull();
  }

  @Test
  void fallbackDoesNotRetainCarrierCycle() {
    WeakReference<ChannelHandler> handlerReference = createCarrierCycle();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          System.gc();
          assertThat(handlerReference.get()).isNull();
        });
  }

  private static WeakReference<ChannelHandler> createCarrierCycle() {
    ChannelHandler handler = new ChannelHandlerAdapter() {};
    VirtualFieldHelper.setInstrumentationHandler(handler, new ReferencingHandler(handler));
    return new WeakReference<>(handler);
  }

  private static final class ReferencingHandler extends ChannelHandlerAdapter {
    private final ChannelHandler handler;

    private ReferencingHandler(ChannelHandler handler) {
      this.handler = handler;
    }
  }
}
