/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.ObjectUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.network.internal.SocketAddressUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NetworkAttributesExtractorSocketAddressTest {

  @ParameterizedTest
  @MethodSource("inetAddresses")
  void inetAddresses(SocketAddress local, SocketAddress peer, Attributes expected) {
    assertThat(extract(new TestGetter(), local, peer)).isEqualTo(expected);
  }

  private static Stream<Arguments> inetAddresses() throws Exception {
    InetSocketAddress ipv4 = new InetSocketAddress(InetAddress.getByName("1.2.3.4"), 8080);
    InetSocketAddress ipv6 = new InetSocketAddress(InetAddress.getByName("2001:db8::1"), 9090);
    InetSocketAddress unresolved = InetSocketAddress.createUnresolved("unresolved.invalid", 1234);
    return Stream.of(
        argumentSet(
            "ipv4 local before ipv6 peer",
            ipv4,
            ipv6,
            Attributes.builder()
                .put(NETWORK_TYPE, "ipv4")
                .put(NETWORK_LOCAL_ADDRESS, "1.2.3.4")
                .put(NETWORK_LOCAL_PORT, 8080)
                .put(NETWORK_PEER_ADDRESS, "2001:db8:0:0:0:0:0:1")
                .put(NETWORK_PEER_PORT, 9090)
                .build()),
        argumentSet(
            "ipv6 local before ipv4 peer",
            ipv6,
            ipv4,
            Attributes.builder()
                .put(NETWORK_TYPE, "ipv6")
                .put(NETWORK_LOCAL_ADDRESS, "2001:db8:0:0:0:0:0:1")
                .put(NETWORK_LOCAL_PORT, 9090)
                .put(NETWORK_PEER_ADDRESS, "1.2.3.4")
                .put(NETWORK_PEER_PORT, 8080)
                .build()),
        argumentSet(
            "peer only",
            null,
            ipv4,
            Attributes.builder()
                .put(NETWORK_TYPE, "ipv4")
                .put(NETWORK_PEER_ADDRESS, "1.2.3.4")
                .put(NETWORK_PEER_PORT, 8080)
                .build()),
        argumentSet("neither address", null, null, Attributes.empty()),
        argumentSet(
            "unresolved local does not fall back to peer type",
            unresolved,
            ipv4,
            Attributes.builder()
                .put(NETWORK_PEER_ADDRESS, "1.2.3.4")
                .put(NETWORK_PEER_PORT, 8080)
                .build()),
        argumentSet(
            "unresolved peer",
            ipv4,
            unresolved,
            Attributes.builder()
                .put(NETWORK_TYPE, "ipv4")
                .put(NETWORK_LOCAL_ADDRESS, "1.2.3.4")
                .put(NETWORK_LOCAL_PORT, 8080)
                .build()),
        argumentSet("both unresolved", unresolved, unresolved, Attributes.empty()));
  }

  @Test
  void unresolvedAddressStillHasPort() {
    SocketAddress unresolved = InetSocketAddress.createUnresolved("unresolved.invalid", 1234);
    TestGetter getter = new TestGetter();
    assertThat(getter.getNetworkLocalAddress(unresolved, unresolved)).isNull();
    assertThat(getter.getNetworkPeerAddress(unresolved, unresolved)).isNull();
    assertThat(getter.getNetworkLocalPort(unresolved, unresolved)).isEqualTo(1234);
    assertThat(getter.getNetworkPeerPort(unresolved, unresolved)).isEqualTo(1234);
  }

  @Test
  void newSocketGetterTakesPrecedenceOverInetGetter() {
    NetworkAttributesGetter<SocketAddress, SocketAddress> getter =
        new TestGetter() {
          @Override
          public InetSocketAddress getNetworkLocalInetSocketAddress(
              SocketAddress request, SocketAddress response) {
            return new InetSocketAddress("1.2.3.4", 8080);
          }

          @Override
          public InetSocketAddress getNetworkPeerInetSocketAddress(
              SocketAddress request, SocketAddress response) {
            return new InetSocketAddress("4.3.2.1", 9090);
          }
        };
    assertThat(
            extract(
                getter,
                new DomainSocketAddress("local.sock"),
                new DomainSocketAddress("peer.sock")))
        .containsOnly(
            entry(NETWORK_TRANSPORT, "unix"),
            entry(NETWORK_LOCAL_ADDRESS, "local.sock"),
            entry(NETWORK_PEER_ADDRESS, "peer.sock"));
  }

  @Test
  void explicitAttributesTakePrecedence() {
    NetworkAttributesGetter<SocketAddress, SocketAddress> getter =
        new TestGetter() {
          @Override
          public String getNetworkType(SocketAddress request, SocketAddress response) {
            return "custom-type";
          }

          @Override
          public String getNetworkTransport(SocketAddress request, SocketAddress response) {
            return "custom-transport";
          }

          @Override
          public String getNetworkLocalAddress(SocketAddress request, SocketAddress response) {
            return "custom-local";
          }

          @Override
          public Integer getNetworkLocalPort(SocketAddress request, SocketAddress response) {
            return 1234;
          }

          @Override
          public String getNetworkPeerAddress(SocketAddress request, SocketAddress response) {
            return "custom-peer";
          }

          @Override
          public Integer getNetworkPeerPort(SocketAddress request, SocketAddress response) {
            return 5678;
          }
        };
    assertThat(
            extract(
                getter,
                new DomainSocketAddress("local.sock"),
                new DomainSocketAddress("peer.sock")))
        .containsOnly(
            entry(NETWORK_TYPE, "custom-type"),
            entry(NETWORK_TRANSPORT, "custom-transport"),
            entry(NETWORK_LOCAL_ADDRESS, "custom-local"),
            entry(NETWORK_LOCAL_PORT, 1234L),
            entry(NETWORK_PEER_ADDRESS, "custom-peer"),
            entry(NETWORK_PEER_PORT, 5678L));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nettyUnixAddresses(boolean shaded) {
    assertUnixAddresses(nettyAddress(shaded, "local.sock"), nettyAddress(shaded, "peer.sock"));
    assertEmptyUnixAddress(nettyAddress(shaded, ""));
  }

  private static SocketAddress nettyAddress(boolean shaded, String path) {
    if (shaded) {
      return new io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress(path);
    }
    return new DomainSocketAddress(path);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_16)
  void jdkUnixAddresses() throws Exception {
    assertUnixAddresses(jdkUnixAddress("local.sock"), jdkUnixAddress("peer.sock"));
    assertEmptyUnixAddress(jdkUnixAddress(""));
  }

  private static SocketAddress jdkUnixAddress(String path) throws Exception {
    return (SocketAddress)
        Class.forName("java.net.UnixDomainSocketAddress")
            .getMethod("of", String.class)
            .invoke(null, path);
  }

  private static void assertUnixAddresses(SocketAddress local, SocketAddress peer) {
    TestGetter getter = new TestGetter();
    assertThat(extract(getter, local, peer))
        .containsOnly(
            entry(NETWORK_TRANSPORT, "unix"),
            entry(NETWORK_LOCAL_ADDRESS, "local.sock"),
            entry(NETWORK_PEER_ADDRESS, "peer.sock"));
    assertThat(extract(getter, null, peer))
        .containsOnly(entry(NETWORK_TRANSPORT, "unix"), entry(NETWORK_PEER_ADDRESS, "peer.sock"));
    assertThat(extract(getter, local, null))
        .containsOnly(entry(NETWORK_TRANSPORT, "unix"), entry(NETWORK_LOCAL_ADDRESS, "local.sock"));
    assertThat(getter.getNetworkLocalPort(local, peer)).isNull();
    assertThat(getter.getNetworkPeerPort(local, peer)).isNull();
    assertThat(getter.getNetworkType(local, peer)).isNull();
  }

  private static void assertEmptyUnixAddress(SocketAddress address) {
    assertThat(extract(new TestGetter(), address, address))
        .containsOnly(entry(NETWORK_TRANSPORT, "unix"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void optionalNettyTypesInDifferentClassLoaders(boolean shaded) throws Exception {
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
    URL helperUrl = SocketAddressUtil.class.getProtectionDomain().getCodeSource().getLocation();
    try (URLClassLoader helperLoader = new URLClassLoader(new URL[] {helperUrl}, null);
        URLClassLoader firstLoader = new URLClassLoader(addressUrls, null);
        URLClassLoader secondLoader = new URLClassLoader(addressUrls, null)) {
      Class<?> helper = helperLoader.loadClass(SocketAddressUtil.class.getName());
      assertThat(helper.getClassLoader()).isNotSameAs(SocketAddressUtil.class.getClassLoader());
      for (URLClassLoader addressLoader : new URLClassLoader[] {firstLoader, secondLoader}) {
        Class<? extends SocketAddress> isolatedType =
            addressLoader.loadClass(addressType.getName()).asSubclass(SocketAddress.class);
        assertThat(isolatedType).isNotSameAs(addressType);
        SocketAddress address =
            isolatedType.getConstructor(String.class).newInstance("isolated.sock");
        assertThat(SocketAddressUtil.getAddress(address)).isEqualTo("isolated.sock");
        assertThat(helper.getMethod("getAddress", SocketAddress.class).invoke(null, address))
            .isEqualTo("isolated.sock");
        assertThat(
                helper
                    .getMethod("getNetworkTransport", SocketAddress.class, SocketAddress.class)
                    .invoke(null, null, address))
            .isEqualTo("unix");
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"java.lang.ClassValue", "java.lang.invoke.MethodHandle", "java.nio.file.Path"})
  void inetAddressesWorkWithoutUnixRuntimeSupport(String missingClass) throws Exception {
    URL helperUrl = SocketAddressUtil.class.getProtectionDomain().getCodeSource().getLocation();
    try (URLClassLoader helperLoader =
        new URLClassLoader(new URL[] {helperUrl}, null) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(missingClass)) {
              throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
          }
        }) {
      Class<?> helper = helperLoader.loadClass(SocketAddressUtil.class.getName());
      InetSocketAddress inet = new InetSocketAddress("1.2.3.4", 8080);
      assertThat(helper.getMethod("getAddress", SocketAddress.class).invoke(null, inet))
          .isEqualTo("1.2.3.4");
      assertThat(helper.getMethod("getPort", SocketAddress.class).invoke(null, inet))
          .isEqualTo(8080);
      assertThat(
              helper
                  .getMethod("getNetworkType", SocketAddress.class, SocketAddress.class)
                  .invoke(null, null, inet))
          .isEqualTo("ipv4");
      assertThat(
              helper
                  .getMethod("getNetworkTransport", SocketAddress.class, SocketAddress.class)
                  .invoke(null, inet, inet))
          .isNull();
      SocketAddress unix = new DomainSocketAddress("unavailable.sock");
      assertThat(helper.getMethod("getAddress", SocketAddress.class).invoke(null, unix)).isNull();
      assertThat(
              helper
                  .getMethod("getNetworkTransport", SocketAddress.class, SocketAddress.class)
                  .invoke(null, unix, unix))
          .isNull();
    }
  }

  @Test
  void unsupportedAddressDoesNotUsePathMethodsOrToString() {
    SocketAddress address = new UnsupportedAddress();
    assertThat(extract(new TestGetter(), address, address)).isEmpty();
    assertThat(SocketAddressUtil.getPort(address)).isNull();
  }

  private static Attributes extract(
      NetworkAttributesGetter<SocketAddress, SocketAddress> getter,
      SocketAddress local,
      SocketAddress peer) {
    AttributesBuilder attributes = Attributes.builder();
    NetworkAttributesExtractor.create(getter).onEnd(attributes, Context.root(), local, peer, null);
    return attributes.build();
  }

  private static class TestGetter implements NetworkAttributesGetter<SocketAddress, SocketAddress> {
    @Override
    public SocketAddress getNetworkLocalSocketAddress(
        SocketAddress request, SocketAddress response) {
      return request;
    }

    @Override
    public SocketAddress getNetworkPeerSocketAddress(
        SocketAddress request, SocketAddress response) {
      return response;
    }
  }

  @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod", "MethodCanBeStatic"})
  private static class UnsupportedAddress extends SocketAddress {
    private static final long serialVersionUID = 1L;

    public String path() {
      throw new AssertionError("Unsupported path accessor");
    }

    public String getPath() {
      throw new AssertionError("Unsupported path accessor");
    }

    @Override
    public String toString() {
      throw new AssertionError("Unsupported address formatting");
    }
  }
}
