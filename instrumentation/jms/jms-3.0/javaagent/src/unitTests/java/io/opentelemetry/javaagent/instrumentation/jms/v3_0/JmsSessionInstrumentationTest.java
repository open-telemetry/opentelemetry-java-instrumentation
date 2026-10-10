/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import jakarta.jms.MessageConsumer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class JmsSessionInstrumentationTest {

  @Test
  void matchesProviderCovariantReturns() {
    assertThat(consumerMatcher().matches(consumerMethod(CustomConsumer.class))).isTrue();
  }

  private static ElementMatcher<? super MethodDescription> consumerMatcher() {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              String adviceClassName = invocation.getArgument(1);
              if (adviceClassName.endsWith("$CreateDurableConsumerAdvice")) {
                matchers.add(invocation.getArgument(0));
              }
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());

    new JmsSessionInstrumentation().transform(transformer);

    assertThat(matchers).hasSize(1);
    return matchers.get(0);
  }

  private static MethodDescription consumerMethod(Class<?> returnType) {
    return new ByteBuddy()
        .subclass(Object.class)
        .name(JmsSessionInstrumentationTest.class.getName() + "$Provider")
        .defineMethod("createDurableConsumer", returnType, Visibility.PUBLIC)
        .withParameters(Object.class, String.class)
        .intercept(StubMethod.INSTANCE)
        .make()
        .getTypeDescription()
        .getDeclaredMethods()
        .filter(named("createDurableConsumer"))
        .getOnly();
  }

  abstract static class CustomConsumer implements MessageConsumer {}
}
