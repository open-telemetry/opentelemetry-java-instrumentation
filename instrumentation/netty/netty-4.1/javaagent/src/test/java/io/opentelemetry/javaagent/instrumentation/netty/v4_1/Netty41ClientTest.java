/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.channel.Channel;
import io.opentelemetry.instrumentation.netty.v4_1.AbstractNetty41ClientTest;
import io.opentelemetry.instrumentation.netty.v4_1.Netty41ClientExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class Netty41ClientTest extends AbstractNetty41ClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @RegisterExtension
  static final Netty41ClientExtension clientExtension =
      new Netty41ClientExtension(channelPipeline -> {});

  @Override
  protected Netty41ClientExtension clientExtension() {
    return clientExtension;
  }

  @Override
  protected void configureChannel(Channel channel) {}

  @Override
  protected boolean testReadTimeout() {
    return true;
  }
}
