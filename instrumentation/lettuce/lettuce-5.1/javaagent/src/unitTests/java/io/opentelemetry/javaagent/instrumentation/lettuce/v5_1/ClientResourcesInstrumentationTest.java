/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class ClientResourcesInstrumentationTest {

  @Test
  void matchesInterfaceAndConcreteBuilderReturns() {
    ElementMatcher<? super MethodDescription> matcher = builderMatcher();

    assertThat(matcher.matches(builderMethod(ClientResources.Builder.class))).isTrue();
    assertThat(
            matcher.matches(
                new TypeDescription.ForLoadedType(DefaultClientResources.class)
                    .getDeclaredMethods()
                    .filter(named("builder"))
                    .getOnly()))
        .isTrue();
  }

  @Test
  void matchesCustomBuilderReturns() {
    assertThat(builderMatcher().matches(builderMethod(CustomBuilder.class))).isTrue();
  }

  @Test
  void rejectsIncompatibleBuilderReturns() {
    ElementMatcher<? super MethodDescription> matcher = builderMatcher();

    assertThat(matcher.matches(builderMethod(Object.class))).isFalse();
    assertThat(matcher.matches(builderMethod(String.class))).isFalse();
    assertThat(matcher.matches(builderMethod(void.class))).isFalse();
  }

  private static ElementMatcher<? super MethodDescription> builderMatcher() {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              String adviceClassName = invocation.getArgument(1);
              if (adviceClassName.endsWith("$BuilderAdvice")) {
                matchers.add(invocation.getArgument(0));
              }
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());

    new ClientResourcesInstrumentation().transform(transformer);

    assertThat(matchers).hasSize(1);
    return matchers.get(0);
  }

  private static MethodDescription builderMethod(Class<?> returnType) {
    return new ByteBuddy()
        .subclass(Object.class)
        .defineMethod("builder", returnType, Visibility.PUBLIC, Ownership.STATIC)
        .intercept(StubMethod.INSTANCE)
        .make()
        .getTypeDescription()
        .getDeclaredMethods()
        .filter(named("builder"))
        .getOnly();
  }

  interface CustomBuilder extends ClientResources.Builder {}
}
