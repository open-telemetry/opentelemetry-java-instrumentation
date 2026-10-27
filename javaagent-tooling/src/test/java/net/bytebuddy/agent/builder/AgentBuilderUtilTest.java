/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package net.bytebuddy.agent.builder;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler.DefineClassContext;
import io.opentelemetry.javaagent.tooling.DefineClassHandler;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class AgentBuilderUtilTest {

  private static final AgentBuilder.Transformer NO_OP =
      (builder, typeDescription, classLoader, module, protectionDomain) -> builder;

  @Test
  void filtersNamedAndSuperTypeTransformationsInRegistrationOrder() throws Exception {
    List<String> matched = new ArrayList<>();
    AgentBuilder builder =
        new AgentBuilder.Default()
            .ignore(none())
            .type(recording(matched, "unrelated").and(named(String.class.getName())))
            .transform(NO_OP)
            .type(recording(matched, "supertype").and(hasSuperType(named(Parent.class.getName()))))
            .transform(NO_OP)
            .type(recording(matched, "unknown"))
            .transform(NO_OP)
            .type(recording(matched, "named").and(named(Child.class.getName())))
            .transform(NO_OP)
            .type(recording(matched, "parent-name").and(named(Parent.class.getName())))
            .transform(NO_OP)
            .type(recording(matched, "interface").and(hasSuperType(named(Marker.class.getName()))))
            .transform(NO_OP);

    assertThat(transform(builder, Child.class, true)).isNotNull();

    assertThat(matched).containsExactly("supertype", "unknown", "named", "interface");
  }

  @Test
  void matchesSuperTypeItselfAndDoesNotDuplicateCandidates() throws Exception {
    List<String> matched = new ArrayList<>();
    AgentBuilder builder =
        new AgentBuilder.Default()
            .ignore(none())
            .type(
                recording(matched, "supertype")
                    .and(
                        hasSuperType(
                            named(Parent.class.getName()).or(named(Child.class.getName())))))
            .transform(NO_OP);

    assertThat(transform(builder, Parent.class, true)).isNotNull();
    assertThat(transform(builder, Child.class, true)).isNotNull();

    assertThat(matched).containsExactly("supertype", "supertype");
  }

  @Test
  void retainsUnknownMatchersWhenNoNamesMatch() throws Exception {
    List<String> matched = new ArrayList<>();
    AgentBuilder builder =
        new AgentBuilder.Default()
            .ignore(none())
            .type(recording(matched, "unrelated").and(named(String.class.getName())))
            .transform(NO_OP)
            .type(recording(matched, "unknown"))
            .transform(NO_OP);

    assertThat(transform(builder, Child.class, true)).isNotNull();

    assertThat(matched).containsExactly("unknown");
  }

  @Test
  void retainsAllMatchersWithoutHierarchyInformation() throws Exception {
    List<String> matched = new ArrayList<>();
    AgentBuilder builder =
        new AgentBuilder.Default()
            .ignore(none())
            .type(recording(matched, "unrelated").and(named(String.class.getName())))
            .transform(NO_OP)
            .type(recording(matched, "named").and(named(Child.class.getName())))
            .transform(NO_OP);

    assertThat(transform(builder, Child.class, false)).isNotNull();

    assertThat(matched).containsExactly("unrelated", "named");
  }

  @Test
  void skipsAllTransformationsWhenNoNamesMatch() throws Exception {
    List<String> matched = new ArrayList<>();
    AgentBuilder builder =
        new AgentBuilder.Default()
            .ignore(none())
            .type(recording(matched, "unrelated").and(named(String.class.getName())))
            .transform(NO_OP);

    assertThat(transform(builder, Child.class, true)).isNull();

    assertThat(matched).isEmpty();
  }

  private static ElementMatcher.Junction<TypeDescription> recording(
      List<String> matched, String name) {
    return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        matched.add(name);
        return true;
      }
    };
  }

  @Nullable
  private static byte[] transform(AgentBuilder builder, Class<?> type, boolean withHierarchy)
      throws IOException, IllegalClassFormatException {
    ClassFileTransformer transformer = AgentBuilderUtil.optimize(builder).makeRaw();
    byte[] bytes = ClassFileLocator.ForClassLoader.read(type);
    DefineClassContext context =
        withHierarchy
            ? DefineClassHandler.INSTANCE.beforeDefineClass(
                type.getClassLoader(), type.getName(), bytes, 0, bytes.length)
            : null;
    try {
      return transformer.transform(
          type.getClassLoader(), type.getName().replace('.', '/'), null, null, bytes);
    } finally {
      DefineClassHandler.INSTANCE.afterDefineClass(context);
    }
  }

  static class Parent {}

  interface Marker {}

  static class Child extends Parent implements Marker {}
}
