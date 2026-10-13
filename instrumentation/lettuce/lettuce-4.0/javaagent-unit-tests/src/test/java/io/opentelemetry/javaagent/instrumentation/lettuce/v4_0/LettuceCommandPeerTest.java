/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.unix.DomainSocketAddress;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LettuceCommandPeerTest {
  private final URLClassLoader classLoader =
      new URLClassLoader(
          new URL[] {LettuceCommandPeer.class.getProtectionDomain().getCodeSource().getLocation()},
          null);

  @AfterEach
  void closeClassLoader() throws IOException {
    classLoader.close();
  }

  @Test
  void cachesMissingNativeTransport() throws Exception {
    Class<?> peerClass = Class.forName(LettuceCommandPeer.class.getName(), true, classLoader);
    Field pathMethod = peerClass.getDeclaredField("domainSocketAddressPathMethod");
    pathMethod.setAccessible(true);
    assertThat(pathMethod.get(null)).isNull();

    Method getAddress = peerClass.getDeclaredMethod("getNetworkPeerAddress", SocketAddress.class);
    getAddress.setAccessible(true);
    assertThat(
            getAddress.invoke(
                null,
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 6379)))
        .isEqualTo("127.0.0.1");
    assertThat(getAddress.invoke(null, new DomainSocketAddress("/var/run/redis.sock"))).isNull();
  }

  @Test
  void cachedMethodReadsEachDomainSocketPath() {
    assertThat(
            LettuceCommandPeer.getNetworkPeerAddress(
                new DomainSocketAddress("/var/run/redis-1.sock")))
        .isEqualTo("/var/run/redis-1.sock");
    assertThat(
            LettuceCommandPeer.getNetworkPeerAddress(
                new DomainSocketAddress("/var/run/redis-2.sock")))
        .isEqualTo("/var/run/redis-2.sock");
  }
}
