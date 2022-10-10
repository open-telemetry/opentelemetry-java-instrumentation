/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.channel.Channel;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class Netty41ClientTest extends AbstractNetty41ClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @RegisterExtension
  static final Netty41ClientExtension clientExtension =
      new Netty41ClientExtension(
          channelPipeline ->
              channelPipeline.addLast(
                  NettyClientTelemetry.builder(testing.getOpenTelemetry())
                      .build()
                      .createCombinedHandler()));

  @Override
  protected Netty41ClientExtension clientExtension() {
    return clientExtension;
  }

  @Override
  protected void configureChannel(Channel channel) {
    // Current context must be propagated to the channel
    NettyClientTelemetry.setChannelContext(channel);
  }

  @Override
  protected boolean testReadTimeout() {
    return false;
  }

  @Override
  protected boolean testErrorWithCallback() {
    return false;
  }

  @Override
  protected boolean testCallbackWithParent() {
    return false;
  }

  @Override
  protected boolean testWithClientParent() {
    return false;
  }

  @Override
  protected boolean testConnectionFailure() {
    return false;
  }

  @Override
  protected boolean testRemoteConnection() {
    return false;
  }
}
