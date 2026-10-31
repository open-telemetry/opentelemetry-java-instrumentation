/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package net.bytebuddy.agent.builder;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentBuilderUtilTest {

  @ParameterizedTest
  @MethodSource("rejectsImpossibleLoadedTypeMatchers")
  void rejectsImpossibleLoadedTypesWithoutEvaluatingDelegate(
      ElementMatcher.Junction<TypeDescription> matcher, Class<?> type) {
    List<String> calls = new ArrayList<>();
    AgentBuilder.Default builder = optimizedBuilder(matcher, calls);

    assertThat(matchesFirstTransformation(builder, type, type)).isFalse();
    assertThat(calls).isEmpty();
  }

  private static Stream<Arguments> rejectsImpossibleLoadedTypeMatchers() {
    return Stream.of(
        argumentSet("unrelated exact name", named(String.class.getName()), Child.class),
        argumentSet("parent name is not child name", named(Parent.class.getName()), Child.class),
        argumentSet(
            "unrelated hierarchy", hasSuperType(named(String.class.getName())), Child.class));
  }

  @ParameterizedTest
  @MethodSource("delegatesPossibleOrUnknownLoadedTypeMatchers")
  void delegatesPossibleOrUnknownLoadedTypes(
      ElementMatcher.Junction<TypeDescription> matcher, Class<?> type, boolean expectedMatch) {
    List<String> calls = new ArrayList<>();
    AgentBuilder.Default builder = optimizedBuilder(matcher, calls);

    assertThat(matchesFirstTransformation(builder, type, type)).isEqualTo(expectedMatch);
    assertThat(calls).containsExactly("classloader", "type");
  }

  private static Stream<Arguments> delegatesPossibleOrUnknownLoadedTypeMatchers() {
    return Stream.of(
        argumentSet("exact name hit", named(Child.class.getName()), Child.class, true),
        argumentSet("superclass", hasSuperType(named(Parent.class.getName())), Child.class, true),
        argumentSet("interface", hasSuperType(named(Marker.class.getName())), Child.class, true),
        argumentSet(
            "supertype itself", hasSuperType(named(Parent.class.getName())), Parent.class, true),
        argumentSet(
            "original matcher rejects interface",
            extendsClass(named(Marker.class.getName())),
            Child.class,
            false),
        argumentSet("unknown matcher", nameStartsWith("net.bytebuddy."), Child.class, true));
  }

  @Test
  void retainsOriginalMatcherWithoutLoadedClass() {
    List<String> calls = new ArrayList<>();
    AgentBuilder.Default builder = optimizedBuilder(named(String.class.getName()), calls);

    assertThat(matchesFirstTransformation(builder, Child.class, null)).isFalse();
    assertThat(calls).containsExactly("classloader", "type");
  }

  private static AgentBuilder.Default optimizedBuilder(
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

  private static boolean matchesFirstTransformation(
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
}
