/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.javaagent.bootstrap.ExceptionLogger;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ExceptionHandlerTest {

  private static final TestHandler testHandler = new TestHandler();
  private static ResettableClassFileTransformer transformer;
  private static Logger exceptionLogger;

  @BeforeAll
  static void setUp() {
    Advice.WithCustomMapping customMapping =
        Advice.withCustomMapping()
            // required for AssignReturned annotation and throwable suppression
            .with(new Advice.AssignReturned.Factory().withSuppressed(Throwable.class));
    AgentBuilder builder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(named(ExceptionHandlerTest.class.getName() + "$SomeClass"))
            .transform(
                new AgentBuilder.Transformer.ForAdvice(customMapping)
                    .with(
                        new AgentBuilder.LocationStrategy.Simple(
                            ClassFileLocator.ForClassLoader.of(BadAdvice.class.getClassLoader())))
                    .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                    .advice(isMethod().and(named("isInstrumented")), BadAdvice.class.getName()))
            .transform(
                new AgentBuilder.Transformer.ForAdvice(customMapping)
                    .with(
                        new AgentBuilder.LocationStrategy.Simple(
                            ClassFileLocator.ForClassLoader.of(BadAdvice.class.getClassLoader())))
                    .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                    .advice(
                        isMethod().and(namedOneOf("smallStack", "largeStack")),
                        BadAdvice.NoOpAdvice.class.getName()));

    ByteBuddyAgent.install();
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation());

    // keep logger in static field to ensure that it won't get gcd before ExceptionLogger
    // class is initialized which would reset logger back to default configuration
    exceptionLogger = Logger.getLogger(ExceptionLogger.class.getName());
    exceptionLogger.setLevel(Level.FINE);
    exceptionLogger.addHandler(testHandler);
  }

  @AfterAll
  static void tearDown() {
    testHandler.close();
    Logger.getLogger(ExceptionLogger.class.getName()).removeHandler(testHandler);

    transformer.reset(
        ByteBuddyAgent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
  }

  @Test
  void exceptionHandlerInvoked() {
    int initLogEvents = testHandler.getRecords().size();

    // Triggers classload and instrumentation
    assertThat(SomeClass.isInstrumented().get())
        .describedAs("method should have been instrumented")
        .isTrue();

    assertThat(testHandler.getRecords())
        .hasSize(initLogEvents + 1)
        .last()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.FINE);
              assertThat(event.getMessage())
                  .startsWith("Failed to handle exception in instrumentation for");
            });
  }

  @Test
  void exceptionOnNondelegatingClassloader() throws Exception {
    int initLogEvents = testHandler.getRecords().size();
    URL[] classpath =
        new URL[] {SomeClass.class.getProtectionDomain().getCodeSource().getLocation()};
    URLClassLoader loader = new URLClassLoader(classpath, null);

    assertThatThrownBy(() -> loader.loadClass(LoggerFactory.class.getName()))
        .isInstanceOf(ClassNotFoundException.class);

    Class<?> someClazz = loader.loadClass(SomeClass.class.getName());
    assertThat(someClazz.getClassLoader()).isSameAs(loader);
    AtomicBoolean instrumented = (AtomicBoolean) someClazz.getMethod("isInstrumented").invoke(null);
    assertThat(testHandler.getRecords()).hasSize(initLogEvents);
    assertThat(instrumented.get()).describedAs("method should have been instrumented").isTrue();
  }

  @Test
  void exceptionHandlerSetsCorrectStackSize() {
    assertThatCode(
            () -> {
              SomeClass.smallStack();
              SomeClass.largeStack();
            })
        .doesNotThrowAnyException();
  }

  public static class SomeClass {

    public static AtomicBoolean isInstrumented() {
      return new AtomicBoolean();
    }

    public static void smallStack() {
      // a method with a max stack of 0
    }

    @SuppressWarnings("SystemOut")
    public static void largeStack() {
      // a method with a max stack of 6
      long l = 22L;
      int i = 3;
      double d = 32.2d;
      Object o = new Object();
      System.out.println("large stack: " + l + ' ' + i + ' ' + d + ' ' + o);
    }

    private SomeClass() {}
  }
}
