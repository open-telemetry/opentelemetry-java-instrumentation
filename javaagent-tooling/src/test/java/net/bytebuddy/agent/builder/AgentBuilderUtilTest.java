/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package net.bytebuddy.agent.builder;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentBuilderUtilTest {

  @ParameterizedTest
  @MethodSource("loadedTypeMatchers")
  void filtersLoadedTypes(
      ElementMatcher.Junction<TypeDescription> matcher,
      Class<?> type,
      boolean expected,
      boolean evaluateDelegate) {
    List<String> calls = new ArrayList<>();
    AgentBuilder.Default builder = builder(matcher, calls);

    assertThat(matches(builder, type, type)).isEqualTo(expected);
    if (evaluateDelegate) {
      assertThat(calls).containsExactly("classloader", "type");
    } else {
      assertThat(calls).isEmpty();
    }
  }

  private static Stream<Arguments> loadedTypeMatchers() {
    return Stream.of(
        argumentSet("exact name", named(Child.class.getName()), Child.class, true, true),
        argumentSet("unrelated name", named(String.class.getName()), Child.class, false, false),
        argumentSet(
            "parent name is not child name",
            named(Parent.class.getName()),
            Child.class,
            false,
            false),
        argumentSet(
            "superclass", hasSuperType(named(Parent.class.getName())), Child.class, true, true),
        argumentSet(
            "interface", hasSuperType(named(Marker.class.getName())), Child.class, true, true),
        argumentSet(
            "supertype itself",
            hasSuperType(named(Parent.class.getName())),
            Parent.class,
            true,
            true),
        argumentSet(
            "unrelated hierarchy",
            hasSuperType(named(String.class.getName())),
            Child.class,
            false,
            false),
        argumentSet(
            "original matcher rejects interface",
            extendsClass(named(Marker.class.getName())),
            Child.class,
            false,
            true),
        argumentSet("unknown matcher", nameStartsWith("net.bytebuddy."), Child.class, true, true));
  }

  @Test
  void retainsOriginalMatcherWithoutLoadedClass() {
    List<String> calls = new ArrayList<>();
    AgentBuilder.Default builder = builder(named(String.class.getName()), calls);

    assertThat(matches(builder, Child.class, null)).isFalse();
    assertThat(calls).containsExactly("classloader", "type");
  }

  @Test
  void retransformsAlreadyLoadedClassAndPreservesTerminalTransformation() {
    Instrumentation instrumentation = ByteBuddyAgent.install();
    LoadedTarget target = new LoadedTarget();
    assertThat(target.value()).isEqualTo("original");
    AgentBuilder builder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .ignore(none())
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(
                new AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Explicit(
                    LoadedTarget.class))
            .type(named(LoadedTarget.class.getName()))
            .transform(
                (dynamicBuilder, typeDescription, classLoader, module, protectionDomain) ->
                    dynamicBuilder
                        .method(named("value"))
                        .intercept(FixedValue.value("instrumented")))
            .asTerminalTransformation();
    AgentBuilder.Default optimized = (AgentBuilder.Default) AgentBuilderUtil.optimize(builder);
    assertThat(optimized.transformations.get(0).isTerminal()).isTrue();
    ResettableClassFileTransformer transformer = optimized.installOn(instrumentation);
    try {
      assertThat(target.value()).isEqualTo("instrumented");
    } finally {
      assertThat(
              transformer.reset(
                  instrumentation, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION))
          .isTrue();
    }
    assertThat(target.value()).isEqualTo("original");
  }

  private static AgentBuilder.Default builder(
      ElementMatcher.Junction<TypeDescription> matcher, List<String> calls) {
    ElementMatcher.Junction<TypeDescription> recording =
        new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
          @Override
          public boolean matches(TypeDescription target) {
            calls.add("type");
            return true;
          }
        };
    return (AgentBuilder.Default)
        AgentBuilderUtil.optimize(
            new AgentBuilder.Default()
                .type(
                    recording.and(matcher),
                    classLoader -> {
                      calls.add("classloader");
                      return true;
                    })
                .transform(
                    (dynamicBuilder, typeDescription, classLoader, module, protectionDomain) ->
                        dynamicBuilder));
  }

  private static boolean matches(
      AgentBuilder.Default builder, Class<?> type, Class<?> classBeingRedefined) {
    return builder
        .transformations
        .get(0)
        .getMatcher()
        .matches(
            TypeDescription.ForLoadedType.of(type),
            type.getClassLoader(),
            JavaModule.ofType(type),
            classBeingRedefined,
            type.getProtectionDomain());
  }

  static class Parent {}

  interface Marker {}

  static class Child extends Parent implements Marker {}

  static class LoadedTarget {
    String value() {
      return "original";
    }
  }
}
