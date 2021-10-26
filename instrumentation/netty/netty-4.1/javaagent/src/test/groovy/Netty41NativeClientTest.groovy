/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueSocketChannel
import org.junit.jupiter.api.Assumptions

// netty client test with epoll/kqueue native library
class Netty41NativeClientTest extends Netty41ClientTest {

  EventLoopGroup buildEventLoopGroup() {
    // linux
    if (Epoll.isAvailable()) {
      return new EpollEventLoopGroup()
    }
    // mac
    if (KQueueHelper.isAvailable()) {
      return new KQueueEventLoopGroup()
    }

    // skip test when native library was not found
    Assumptions.assumeTrue(false, "Native library was not found")
    return super.buildEventLoopGroup()
  }

  @Override
  Class<Channel> getChannelClass() {
    if (Epoll.isAvailable()) {
      return EpollSocketChannel
    }
    if (KQueueHelper.isAvailable()) {
      return KQueueSocketChannel
    }
    return null
  }

  static class KQueueHelper {
    static boolean isAvailable() {
      try {
        return KQueue.isAvailable()
      } catch (NoClassDefFoundError error) {
        // kqueue is available only in latest dep tests
        // in regular tests we only have a compile time dependency because kqueue support was added
        // after 4.1.0
        return false
      }
    }
  }
}