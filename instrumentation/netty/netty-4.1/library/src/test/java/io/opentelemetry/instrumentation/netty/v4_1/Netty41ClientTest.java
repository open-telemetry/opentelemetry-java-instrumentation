/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty41ClientTest extends AbstractNetty41ClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @RegisterExtension
  static final Netty41ClientExtension clientExtension =
      new Netty41ClientExtension(
          channelPipeline ->
              channelPipeline.addLast(
                  NettyClientTelemetry.builder(testing.getOpenTelemetry())
                      .setCapturedRequestHeaders(
                          Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
                      .setCapturedResponseHeaders(
                          Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
                      .build()
                      .createCombinedHandler()));

  @Override
  protected Netty41ClientExtension clientExtension() {
    return clientExtension;
  }

  @Override
  protected void configureChannel(Channel channel) {
    // Current context must be propagated to the channel
    NettyClientTelemetry.setChannelContext(channel, Context.current());
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    optionsBuilder.disableTestErrorWithCallback();
    optionsBuilder.disableTestCallbackWithParent();
    optionsBuilder.disableTestWithClientParent();
    optionsBuilder.disableTestConnectionFailure();
    optionsBuilder.disableTestRemoteConnection();
  }
}
