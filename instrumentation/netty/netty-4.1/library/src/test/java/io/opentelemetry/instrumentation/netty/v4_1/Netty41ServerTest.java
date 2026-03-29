/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static java.util.Collections.singletonList;

import io.netty.channel.ChannelPipeline;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.testing.internal.io.netty.handler.codec.http.HttpServerCodec;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty41ServerTest extends AbstractNetty41ServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configurePipeline(ChannelPipeline channelPipeline) {
    channelPipeline.addAfter(
        HttpServerCodec.class.getSimpleName() + "#0",
        NettyServerTelemetry.class.getName(),
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build()
            .createCombinedHandler());
  }
}
