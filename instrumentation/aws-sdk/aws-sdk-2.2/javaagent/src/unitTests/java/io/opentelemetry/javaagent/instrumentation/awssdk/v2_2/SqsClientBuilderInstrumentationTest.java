/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqsClientBuilderInstrumentationTest {

  @ParameterizedTest
  @MethodSource("builders")
  void matchesClientReturn(TypeInstrumentation instrumentation, String clientClassName) {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              matchers.add(invocation.getArgument(0));
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());
    instrumentation.transform(transformer);

    assertThat(matchers).hasSize(1);
    ElementMatcher<? super MethodDescription> matcher = matchers.get(0);
    TypeDescription client =
        new ByteBuddy().makeInterface().name(clientClassName).make().getTypeDescription();
    assertThat(matcher.matches(buildClientMethod(client))).isTrue();
  }

  private static Stream<Arguments> builders() {
    return Stream.of(
        arguments(
            new DefaultSqsClientBuilderInstrumentation(),
            "software.amazon.awssdk.services.sqs.SqsClient"),
        arguments(
            new DefaultSqsAsyncClientBuilderInstrumentation(),
            "software.amazon.awssdk.services.sqs.SqsAsyncClient"));
  }

  private static MethodDescription buildClientMethod(TypeDescription returnType) {
    return new ByteBuddy()
        .subclass(Object.class)
        .defineMethod("buildClient", returnType, Visibility.PROTECTED)
        .intercept(StubMethod.INSTANCE)
        .make()
        .getTypeDescription()
        .getDeclaredMethods()
        .filter(named("buildClient"))
        .getOnly();
  }
}
