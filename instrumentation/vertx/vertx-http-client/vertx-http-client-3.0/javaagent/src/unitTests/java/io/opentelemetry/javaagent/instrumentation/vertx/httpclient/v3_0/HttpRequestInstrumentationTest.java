/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class HttpRequestInstrumentationTest {

  @Test
  void matchesInterfaceAndConcreteResponseSignatures() {
    ElementMatcher<? super MethodDescription> matcher = responseMatcher();
    TypeDescription response =
        new ByteBuddy()
            .makeInterface()
            .name("io.vertx.core.http.HttpClientResponse")
            .make()
            .getTypeDescription();
    TypeDescription implementation =
        new ByteBuddy()
            .subclass(Object.class)
            .implement(response)
            .name("io.vertx.core.http.impl.HttpClientResponseImpl")
            .make()
            .getTypeDescription();
    TypeDescription timeout = new TypeDescription.ForLoadedType(long.class);

    // Vert.x 3.0/3.4 declare the implementation type; 3.9 declares the interface type.
    assertThat(matcher.matches(responseMethod(implementation))).isTrue();
    assertThat(matcher.matches(responseMethod(response))).isTrue();
    assertThat(matcher.matches(responseMethod(implementation, timeout))).isTrue();
    assertThat(matcher.matches(responseMethod(response, timeout))).isTrue();
  }

  @Test
  void rejectsMissingOrIncompatibleResponseArguments() {
    ElementMatcher<? super MethodDescription> matcher = responseMatcher();

    assertThat(matcher.matches(responseMethod())).isFalse();
    assertThat(matcher.matches(responseMethod(new TypeDescription.ForLoadedType(Object.class))))
        .isFalse();
    assertThat(matcher.matches(responseMethod(new TypeDescription.ForLoadedType(String.class))))
        .isFalse();
    assertThat(matcher.matches(responseMethod(new TypeDescription.ForLoadedType(int.class))))
        .isFalse();
  }

  private static ElementMatcher<? super MethodDescription> responseMatcher() {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              String adviceClassName = invocation.getArgument(1);
              if (adviceClassName.endsWith("$HandleResponseAdvice")) {
                matchers.add(invocation.getArgument(0));
              }
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());

    new HttpRequestInstrumentation().transform(transformer);

    assertThat(matchers).hasSize(1);
    return matchers.get(0);
  }

  private static MethodDescription responseMethod(TypeDescription... arguments) {
    return new ByteBuddy()
        .subclass(Object.class)
        .defineMethod("handleResponse", void.class, Visibility.PACKAGE_PRIVATE)
        .withParameters(arguments)
        .intercept(StubMethod.INSTANCE)
        .make()
        .getTypeDescription()
        .getDeclaredMethods()
        .filter(named("handleResponse"))
        .getOnly();
  }
}
