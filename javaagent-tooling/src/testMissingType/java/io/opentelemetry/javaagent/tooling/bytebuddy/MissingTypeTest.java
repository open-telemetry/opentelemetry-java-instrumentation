/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.common.base.Joiner;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MissingTypeTest {
  private static final Logger logger = LoggerFactory.getLogger(MissingTypeTest.class);

  private static ResettableClassFileTransformer transformer;
  private static final AtomicBoolean hasErrors = new AtomicBoolean(false);

  @BeforeAll
  static void setUp() {
    AgentBuilder builder =
        new AgentBuilder.Default(
                new ByteBuddy().with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE))
            .with(AgentBuilder.TypeStrategy.Default.DECORATE)
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(
                new AgentBuilder.Listener.Adapter() {
                  @Override
                  public void onError(
                      String typeName,
                      ClassLoader classLoader,
                      JavaModule module,
                      boolean loaded,
                      Throwable throwable) {
                    logger.error("Transformation error", throwable);
                    hasErrors.set(true);
                  }
                })
            .type(named(MissingTypeTest.class.getName() + "$SomeClass"))
            .transform(
                new AgentBuilder.Transformer.ForAdvice()
                    .with(
                        new AgentBuilder.LocationStrategy.Simple(
                            ClassFileLocator.ForClassLoader.of(TestAdvice.class.getClassLoader())))
                    .advice(isMethod().and(named("isInstrumented")), TestAdvice.class.getName()));

    ByteBuddyAgent.install();
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation());
  }

  @AfterAll
  static void tearDown() {
    transformer.reset(
        ByteBuddyAgent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
  }

  @Test
  void guavaNotAvailable() {
    // these tests expect com.google.common.base.Joiner to be missing from runtime class path
    try {
      Class.forName("com.google.common.base.Joiner");
      fail("guava should not be available during runtime");
    } catch (ClassNotFoundException exception) {
      // ignore
    }
  }

  @Test
  void instrumented() {
    assertThat(SomeClass.isInstrumented()).isTrue();
  }

  @Test
  void hasNoErrors() {
    assertThat(hasErrors.get()).isFalse();
  }

  // com.google.common.base.Joiner is missing from runtime class path
  static class SomeClass {
    public Joiner joiner;

    public static boolean isInstrumented() {
      return false;
    }

    public void methodWithMissingParameterType(Joiner joiner) {}

    public Joiner methodWithMissingReturnType() {
      return null;
    }

    public static void staticMethodWithMissingParameterType(Joiner joiner) {}

    public static Joiner staticMethodWithMissingReturnType() {
      return null;
    }
  }
}
