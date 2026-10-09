/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.SocketAddress;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.StubMethod;
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
  void matchesKnownPromiseSignatures(
      Class<?> addressType, String promiseType, Class<?> indexType, boolean expected) {
    TypeDescription promise =
        new ByteBuddy().makeInterface().name(promiseType).make().getTypeDescription();
    MethodDescription method =
        new ByteBuddy()
            .subclass(Object.class)
            .defineMethod("doConnect", void.class, Visibility.PACKAGE_PRIVATE)
            .withParameters(
                new TypeDescription.ForLoadedType(addressType),
                new TypeDescription.ForLoadedType(Object.class),
                promise,
                new TypeDescription.ForLoadedType(indexType))
            .intercept(StubMethod.INSTANCE)
            .make()
            .getTypeDescription()
            .getDeclaredMethods()
            .filter(named("doConnect"))
            .getOnly();

    assertThat(matcherCaptor.getValue().matches(method)).isEqualTo(expected);
  }

  private static Stream<Arguments> connectionSignatures() {
    return Stream.of(
        Arguments.of(List.class, "io.netty.channel.ChannelPromise", int.class, true),
        Arguments.of(
            List.class,
            "reactor.netty.transport.TransportConnector$MonoChannelPromise",
            int.class,
            true),
        Arguments.of(List.class, "io.netty.channel.DefaultChannelPromise", int.class, false),
        Arguments.of(List.class, "java.lang.Object", int.class, false),
        Arguments.of(SocketAddress.class, "io.netty.channel.ChannelPromise", int.class, false),
        Arguments.of(List.class, "io.netty.channel.ChannelPromise", long.class, false));
  }
}
