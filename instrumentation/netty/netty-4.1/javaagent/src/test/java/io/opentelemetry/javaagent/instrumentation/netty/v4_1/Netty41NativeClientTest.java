/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.opentelemetry.instrumentation.netty.v4_1.AbstractNetty41ClientTest;
import io.opentelemetry.instrumentation.netty.v4_1.Netty41ClientExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Netty client test with epoll/kqueue native library. */
public class Netty41NativeClientTest extends AbstractNetty41ClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @RegisterExtension
  static final Netty41ClientExtension clientExtension =
      new Netty41ClientExtension(
          channelPipeline -> {}, Netty41NativeClientTest::buildEventLoopGroup, getChannelClass());

  private static EventLoopGroup buildEventLoopGroup() {
    // linux
    if (Epoll.isAvailable()) {
      return new EpollEventLoopGroup();
    }
    // mac
    if (KQueueHelper.isAvailable()) {
      return new KQueueEventLoopGroup();
    }
    // skip test when native library was not found
    Assumptions.assumeTrue(false, "Native library was not found");
    return new NioEventLoopGroup();
  }

  @SuppressWarnings("AbbreviationAsWordInName")
  private static class KQueueHelper {
    static boolean isAvailable() {
      try {
        return KQueue.isAvailable();
      } catch (NoClassDefFoundError error) {
        // kqueue is available only in latest dep tests
        // in regular tests we only have a compile time dependency because kqueue support was added
        // after 4.1.0
        return false;
      }
    }
  }

  private static Class<? extends Channel> getChannelClass() {
    if (Epoll.isAvailable()) {
      return EpollSocketChannel.class;
    }
    if (KQueueHelper.isAvailable()) {
      return KQueueSocketChannel.class;
    }
    return NioSocketChannel.class;
  }

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
