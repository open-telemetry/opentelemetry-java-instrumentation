/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class AdviceInspectorTest {
  private static final AdviceInspector adviceInspector =
      new AdviceInspector(
          ClassFileLocator.ForClassLoader.of(AdviceInspectorTest.class.getClassLoader()));

  @Test
  void defaultValue() {
    assertThat(
            adviceInspector.useIndy(
                new InstrumentationModule("test") {
                  @Override
                  public List<TypeInstrumentation> typeInstrumentations() {
                    return emptyList();
                  }
                }))
        .isFalse();
  }

  @Test
  void hasNonInlineAdvice() {
    assertThat(
            adviceInspector.useIndy(
                new InstrumentationModule("test") {
                  @Override
                  public List<TypeInstrumentation> typeInstrumentations() {
                    return singletonList(new TestInstrumentation(NonInlineAdvice.class));
                  }
                }))
        .isTrue();
  }

  @Test
  void mixedInlineAdviceFirst() {
    assertThat(
            adviceInspector.useIndy(
                new InstrumentationModule("test") {
                  @Override
                  public List<TypeInstrumentation> typeInstrumentations() {
                    return singletonList(
                        new TestInstrumentation(InlineAdvice.class, NonInlineAdvice.class));
                  }
                }))
        .isTrue();
  }

  private static class TestInstrumentation implements TypeInstrumentation {
    private final Class<?>[] adviceClasses;

    TestInstrumentation(Class<?>... adviceClasses) {
      this.adviceClasses = adviceClasses;
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return none();
    }

    @Override
    public void transform(TypeTransformer transformer) {
      for (Class<?> adviceClass : adviceClasses) {
        transformer.applyAdviceToMethod(none(), adviceClass.getName());
      }
    }
  }

  private static class NonInlineAdvice {
    @Advice.OnMethodEnter(inline = false)
    public static void enter() {}
  }

  private static class InlineAdvice {
    @Advice.OnMethodEnter
    public static void enter() {}
  }
}
