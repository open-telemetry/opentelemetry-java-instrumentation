/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.ObjectUtil;
import io.opentelemetry.instrumentation.api.semconv.network.internal.UnixSocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcSocketAddressUtilTest {

  @ParameterizedTest
  @ValueSource(strings = {"peer.sock", ""})
  void normalizesBothNettyTransports(String path) {
    DomainSocketAddress address = new DomainSocketAddress(path);
    assertThat(GrpcSocketAddressUtil.normalize(address))
        .isInstanceOfSatisfying(
            UnixSocketAddress.class,
            normalized -> {
              assertThat(normalized.getPath()).isEqualTo(path);
              assertThat(normalized.getOriginalAddress()).isSameAs(address);
            });
    SocketAddress shadedAddress =
        new io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress(path);
    assertThat(GrpcSocketAddressUtil.normalize(shadedAddress))
        .isInstanceOfSatisfying(
            UnixSocketAddress.class,
            normalized -> {
              assertThat(normalized.getPath()).isEqualTo(path);
              assertThat(normalized.getOriginalAddress()).isSameAs(shadedAddress);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void normalizesNettyAddressesFromDifferentClassLoaders(boolean shaded) throws Exception {
    Class<?> addressType =
        shaded
            ? io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress.class
            : DomainSocketAddress.class;
    URL addressJar = addressType.getProtectionDomain().getCodeSource().getLocation();
    URL[] addressUrls =
        shaded
            ? new URL[] {addressJar}
            : new URL[] {
              addressJar, ObjectUtil.class.getProtectionDomain().getCodeSource().getLocation()
            };
    try (URLClassLoader firstLoader = new URLClassLoader(addressUrls, null);
        URLClassLoader secondLoader = new URLClassLoader(addressUrls, null)) {
      for (URLClassLoader loader : new URLClassLoader[] {firstLoader, secondLoader}) {
        SocketAddress address =
            loader
                .loadClass(addressType.getName())
                .asSubclass(SocketAddress.class)
                .getConstructor(String.class)
                .newInstance("isolated.sock");
        assertThat(GrpcSocketAddressUtil.normalize(address))
            .isInstanceOfSatisfying(
                UnixSocketAddress.class,
                normalized -> {
                  assertThat(normalized.getPath()).isEqualTo("isolated.sock");
                  assertThat(normalized.getOriginalAddress()).isSameAs(address);
                });
      }
    }
  }

  @Test
  void passesThroughInetAddresses() {
    SocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 8080);
    assertThat(GrpcSocketAddressUtil.normalize(address)).isSameAs(address);
  }
}
