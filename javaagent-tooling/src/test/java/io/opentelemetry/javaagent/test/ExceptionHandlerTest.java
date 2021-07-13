/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.javaagent.bootstrap.ExceptionLogger;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import java.net.URL;
import java.net.URLClassLoader;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ExceptionHandlerTest {

  private static final ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
  private static ResettableClassFileTransformer transformer;

  @BeforeAll
  static void setUp() {
    AgentBuilder builder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(named(ExceptionHandlerTest.class.getName() + "$SomeClass"))
            .transform(
                new AgentBuilder.Transformer.ForAdvice()
                    .with(
                        new AgentBuilder.LocationStrategy.Simple(
                            ClassFileLocator.ForClassLoader.of(BadAdvice.class.getClassLoader())))
                    .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                    .advice(isMethod().and(named("isInstrumented")), BadAdvice.class.getName()))
            .transform(
                new AgentBuilder.Transformer.ForAdvice()
                    .with(
                        new AgentBuilder.LocationStrategy.Simple(
                            ClassFileLocator.ForClassLoader.of(BadAdvice.class.getClassLoader())))
                    .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                    .advice(
                        isMethod().and(namedOneOf("smallStack", "largeStack")),
                        BadAdvice.NoOpAdvice.class.getName()));

    ByteBuddyAgent.install();
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation());

    Logger logger = (Logger) LoggerFactory.getLogger(ExceptionLogger.class);
    testAppender.setContext(logger.getLoggerContext());
    logger.addAppender(testAppender);
    testAppender.start();
  }

  @AfterAll
  static void tearDown() {
    testAppender.stop();
    transformer.reset(
        ByteBuddyAgent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
  }

  @Test
  void exceptionHandlerInvoked() {
    int initLogEvents = testAppender.list.size();

    // Triggers classload and instrumentation
    assertThat(SomeClass.isInstrumented()).isTrue();
    assertThat(testAppender.list)
        .hasSize(initLogEvents + 1)
        .last()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
              assertThat(event.getMessage())
                  .startsWith("Failed to handle exception in instrumentation for");
            });
  }

  @Test
  void exceptionOnNondelegatingClassloader() throws Exception {
    int initLogEvents = testAppender.list.size();
    URL[] classpath =
        new URL[] {SomeClass.class.getProtectionDomain().getCodeSource().getLocation()};
    URLClassLoader loader = new URLClassLoader(classpath, null);

    assertThatThrownBy(() -> loader.loadClass(LoggerFactory.class.getName()))
        .isInstanceOf(ClassNotFoundException.class);

    Class<?> someClazz = loader.loadClass(SomeClass.class.getName());
    assertThat(someClazz.getClassLoader()).isSameAs(loader);
    someClazz.getMethod("isInstrumented").invoke(null);
    assertThat(testAppender.list).hasSize(initLogEvents);
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
    public static boolean isInstrumented() {
      return false;
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
  }
}
