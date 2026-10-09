/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.SocketAddress;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransportConnectorInstrumentationTest {
  @Mock private TypeTransformer transformer;

  @Captor private ArgumentCaptor<ElementMatcher<? super MethodDescription>> matcherCaptor;

  @BeforeEach
  void captureMatcher() {
    new TransportConnectorInstrumentation().transform(transformer);
    verify(transformer)
        .applyAdviceToMethod(
            matcherCaptor.capture(),
            eq(TransportConnectorInstrumentation.class.getName() + "$ConnectNewAdvice"));
  }

  @ParameterizedTest
  @MethodSource("connectionSignatures")
  void matchesCompatiblePromiseSignatures(Class<?>[] parameterTypes, boolean expected)
      throws Exception {
    MethodDescription method =
        new MethodDescription.ForLoadedMethod(
            ConnectionSignatures.class.getDeclaredMethod("doConnect", parameterTypes));

    assertThat(matcherCaptor.getValue().matches(method)).isEqualTo(expected);
  }

  private static Stream<Arguments> connectionSignatures() {
    return Stream.of(
        Arguments.of(
            new Class<?>[] {List.class, Object.class, ChannelPromise.class, int.class}, true),
        Arguments.of(
            new Class<?>[] {List.class, Object.class, MonoChannelPromise.class, int.class}, true),
        Arguments.of(
            new Class<?>[] {List.class, Object.class, DefaultChannelPromise.class, int.class},
            true),
        Arguments.of(new Class<?>[] {List.class, Object.class, Object.class, int.class}, false),
        Arguments.of(
            new Class<?>[] {SocketAddress.class, Object.class, ChannelPromise.class, int.class},
            false),
        Arguments.of(
            new Class<?>[] {List.class, Object.class, ChannelPromise.class, long.class}, false));
  }

  // Models the concrete ChannelPromise parameter introduced in reactor-netty 1.0.34.
  private abstract static class MonoChannelPromise implements ChannelPromise {}

  @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MethodCanBeStatic"})
  private static class ConnectionSignatures {
    void doConnect(List<SocketAddress> addresses, Object bind, ChannelPromise promise, int index) {}

    void doConnect(
        List<SocketAddress> addresses, Object bind, MonoChannelPromise promise, int index) {}

    void doConnect(
        List<SocketAddress> addresses, Object bind, DefaultChannelPromise promise, int index) {}

    void doConnect(List<SocketAddress> addresses, Object bind, Object promise, int index) {}

    void doConnect(SocketAddress address, Object bind, ChannelPromise promise, int index) {}

    void doConnect(
        List<SocketAddress> addresses, Object bind, ChannelPromise promise, long index) {}
  }
}
