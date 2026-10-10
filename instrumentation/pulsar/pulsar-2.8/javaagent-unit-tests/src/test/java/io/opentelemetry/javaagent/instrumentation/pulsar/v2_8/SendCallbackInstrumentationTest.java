/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
class SendCallbackInstrumentationTest {
  @Mock private TypeTransformer transformer;

  @Captor private ArgumentCaptor<ElementMatcher<? super MethodDescription>> matcherCaptor;

  @BeforeEach
  void captureMatcher() {
    new SendCallbackInstrumentation().transform(transformer);
    verify(transformer)
        .applyAdviceToMethod(
            matcherCaptor.capture(),
            eq(SendCallbackInstrumentation.class.getName() + "$SendCallbackSendCompleteAdvice"));
  }

  @ParameterizedTest
  @MethodSource("callbackSignatures")
  void matchesCompatibleCallbackSignatures(String methodName, Class<?>[] parameterTypes)
      throws Exception {
    MethodDescription method =
        new MethodDescription.ForLoadedMethod(
            CallbackSignatures.class.getDeclaredMethod(methodName, parameterTypes));

    assertThat(matcherCaptor.getValue().matches(method)).isTrue();
  }

  private static Stream<Arguments> callbackSignatures() {
    return Stream.of(
        Arguments.of("sendComplete", new Class<?>[] {Exception.class}),
        Arguments.of("sendComplete", new Class<?>[] {Throwable.class}),
        Arguments.of("sendComplete", new Class<?>[] {Throwable.class, Object.class}));
  }

  @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MethodCanBeStatic"})
  private static class CallbackSignatures {
    void sendComplete(Exception failure) {}

    void sendComplete(Throwable failure) {}

    void sendComplete(Throwable failure, Object stats) {}
  }
}
