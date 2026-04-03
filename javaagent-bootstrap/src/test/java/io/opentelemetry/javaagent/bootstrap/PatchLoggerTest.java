/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.OFF;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

class PatchLoggerTest {
  @Test
  void testImplementsAllMethods() {
    Set<MethodSignature> patchLoggerMethods = new HashSet<>();
    for (Method method : PatchLogger.class.getMethods()) {
      MethodSignature methodSignature = new MethodSignature();
      methodSignature.name = method.getName();
      methodSignature.modifiers = method.getModifiers();
      for (Class<?> clazz : method.getParameterTypes()) {
        String parameterType = clazz.getName();
        methodSignature.parameterTypes.add(
            parameterType.replaceFirst(
                "io.opentelemetry.javaagent.bootstrap.PatchLogger", "java.util.logging.Logger"));
      }
      methodSignature.returnType =
          method
              .getReturnType()
              .getName()
              .replace(
                  "io.opentelemetry.javaagent.bootstrap.PatchLogger", "java.util.logging.Logger");
      patchLoggerMethods.add(methodSignature);
    }
    Set<MethodSignature> julLoggerMethods = new HashSet<>();
    for (Method method : Logger.class.getMethods()) {
      String methodName = method.getName();
      MethodSignature builder = new MethodSignature();
      builder.name = methodName;
      builder.modifiers = method.getModifiers();
      List<String> parameterTypes = new ArrayList<>();
      for (Class<?> clazz : method.getParameterTypes()) {
        parameterTypes.add(clazz.getName());
      }
      builder.parameterTypes.addAll(parameterTypes);
      builder.returnType = method.getReturnType().getName();
      julLoggerMethods.add(builder);
    }
    assertThat(patchLoggerMethods).containsAll(julLoggerMethods);
  }

  @Test
  void testGetName() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.name()).thenReturn("xyz");
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getName()).isEqualTo("xyz");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("normalMethodArgs")
  void testNormalMethods(
      BiConsumer<PatchLogger, String> invocation,
      String message,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    invocation.accept(logger, message);

    verify(internalLogger).log(expectedLevel, message, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testParameterizedLevelMethodsWithNoParams(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.log(level, message);

    verify(internalLogger).log(expectedLevel, message, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattedLevelArgs")
  void testParameterizedLevelMethodsWithSingleParam(
      Level level,
      String template,
      String arg,
      String expectedMessage,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.log(level, template, arg);

    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(expectedLevel);
    inOrder.verify(internalLogger).log(expectedLevel, expectedMessage, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattedArrayLevelArgs")
  void testParameterizedLevelMethodsWithArrayOfParams(
      Level level,
      String template,
      Object[] args,
      String expectedMessage,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.log(level, template, args);

    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(expectedLevel);
    inOrder.verify(internalLogger).log(expectedLevel, expectedMessage, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testParameterizedLevelMethodsWithThrowable(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable t = new Throwable(message);

    logger.log(level, message, t);

    verify(internalLogger).log(expectedLevel, message, t);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testIsLoggableAll() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);

    // when
    PatchLogger logger = new PatchLogger(internalLogger);

    // then
    assertThat(logger.isLoggable(SEVERE)).isTrue();
    assertThat(logger.isLoggable(WARNING)).isTrue();
    assertThat(logger.isLoggable(INFO)).isTrue();
    assertThat(logger.isLoggable(CONFIG)).isTrue();
    assertThat(logger.isLoggable(FINE)).isTrue();
    assertThat(logger.isLoggable(FINER)).isTrue();
    assertThat(logger.isLoggable(FINEST)).isTrue();
  }

  @Test
  void testIsLoggableSome() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(InternalLogger.Level.ERROR)).thenReturn(true);
    when(internalLogger.isLoggable(InternalLogger.Level.WARN)).thenReturn(true);

    // when
    PatchLogger logger = new PatchLogger(internalLogger);

    // then
    assertThat(logger.isLoggable(SEVERE)).isTrue();
    assertThat(logger.isLoggable(WARNING)).isTrue();
    assertThat(logger.isLoggable(INFO)).isFalse();
    assertThat(logger.isLoggable(CONFIG)).isFalse();
    assertThat(logger.isLoggable(FINE)).isFalse();
    assertThat(logger.isLoggable(FINER)).isFalse();
    assertThat(logger.isLoggable(FINEST)).isFalse();
  }

  @Test
  void testIsLoggableNone() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);

    // when
    PatchLogger logger = new PatchLogger(internalLogger);

    // then
    assertThat(logger.isLoggable(SEVERE)).isFalse();
    assertThat(logger.isLoggable(WARNING)).isFalse();
    assertThat(logger.isLoggable(INFO)).isFalse();
    assertThat(logger.isLoggable(CONFIG)).isFalse();
    assertThat(logger.isLoggable(FINE)).isFalse();
    assertThat(logger.isLoggable(FINER)).isFalse();
    assertThat(logger.isLoggable(FINEST)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getLevelArgs")
  void testGetLevel(InternalLogger.Level enabledLevel, Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(enabledLevel)).thenReturn(true);

    PatchLogger logger = new PatchLogger(internalLogger);

    assertThat(logger.getLevel()).isEqualTo(expectedLevel);
  }

  @Test
  void testGetLevelOff() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(OFF);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testLogpParameterizedLevelMethodsWithNoParams(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.logp(level, null, null, message);

    verify(internalLogger).log(expectedLevel, message, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattedLevelArgs")
  void testLogpParameterizedLevelMethodsWithSingleParam(
      Level level,
      String template,
      String arg,
      String expectedMessage,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.logp(level, null, null, template, arg);

    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(expectedLevel);
    inOrder.verify(internalLogger).log(expectedLevel, expectedMessage, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattedArrayLevelArgs")
  void testLogpParameterizedLevelMethodsWithArrayOfParams(
      Level level,
      String template,
      Object[] args,
      String expectedMessage,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.logp(level, null, null, template, args);

    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(expectedLevel);
    inOrder.verify(internalLogger).log(expectedLevel, expectedMessage, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testLogpParameterizedLevelMethodsWithThrowable(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable t = new Throwable(message);

    logger.logp(level, null, null, message, t);

    verify(internalLogger).log(expectedLevel, message, t);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testLogrbParameterizedLevelMethodsWithNoParams(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.logrb(level, null, null, null, message);

    verify(internalLogger).log(expectedLevel, message, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattedLevelArgs")
  void testLogrbParameterizedLevelMethodsWithSingleParam(
      Level level,
      String template,
      String arg,
      String expectedMessage,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.logrb(level, null, null, null, template, arg);

    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(expectedLevel);
    inOrder.verify(internalLogger).log(expectedLevel, expectedMessage, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattedArrayLevelArgs")
  void testLogrbParameterizedLevelMethodsWithArrayOfParams(
      Level level,
      String template,
      Object[] args,
      String expectedMessage,
      InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    logger.logrb(level, null, null, (String) null, template, args);

    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(expectedLevel);
    inOrder.verify(internalLogger).log(expectedLevel, expectedMessage, null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithVarArgsOfParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logrb(SEVERE, (String) null, null, null, "ereves: {0},{1}", "a", "b");
    logger.logrb(WARNING, (String) null, null, null, "gninraw: {0},{1}", "b", "c");
    logger.logrb(INFO, (String) null, null, null, "ofni: {0},{1}", "c", "d");
    logger.logrb(CONFIG, (String) null, null, null, "gifnoc: {0},{1}", "d", "e");
    logger.logrb(FINE, (String) null, null, null, "enif: {0},{1}", "e", "f");
    logger.logrb(FINER, (String) null, null, null, "renif: {0},{1}", "f", "g");
    logger.logrb(FINEST, (String) null, null, null, "tsenif: {0},{1}", "g", "h");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "gifnoc: d,e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e,f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f,g", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g,h", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithVarArgsOfParams2() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logrb(SEVERE, (ResourceBundle) null, "ereves: {0},{1}", "a", "b");
    logger.logrb(WARNING, (ResourceBundle) null, "gninraw: {0},{1}", "b", "c");
    logger.logrb(INFO, (ResourceBundle) null, "ofni: {0},{1}", "c", "d");
    logger.logrb(CONFIG, (ResourceBundle) null, "gifnoc: {0},{1}", "d", "e");
    logger.logrb(FINE, (ResourceBundle) null, "enif: {0},{1}", "e", "f");
    logger.logrb(FINER, (ResourceBundle) null, "renif: {0},{1}", "f", "g");
    logger.logrb(FINEST, (ResourceBundle) null, "tsenif: {0},{1}", "g", "h");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "gifnoc: d,e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e,f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f,g", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g,h", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testLogrbParameterizedLevelMethodsWithThrowable(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable t = new Throwable(message);

    logger.logrb(level, null, null, (String) null, message, t);

    verify(internalLogger).log(expectedLevel, message, t);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testLogrbParameterizedLevelMethodsWithThrowable2(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable t = new Throwable(message);

    logger.logrb(level, null, null, (ResourceBundle) null, message, t);

    verify(internalLogger).log(expectedLevel, message, t);
    verifyNoMoreInteractions(internalLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleLevelArgs")
  void testLogrbParameterizedLevelMethodsWithResourceBundleObjectAndThrowable(
      Level level, String message, InternalLogger.Level expectedLevel) {
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable t = new Throwable(message);

    logger.logrb(level, null, null, (ResourceBundle) null, message, t);

    verify(internalLogger).log(expectedLevel, message, t);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testEnteringExitingThrowingMethods() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.entering(null, null);
    logger.entering(null, null, new Object());
    logger.entering(null, null, new Object[0]);
    logger.exiting(null, null);
    logger.exiting(null, null, new Object());
    logger.throwing(null, null, null);

    // then
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testResourceBundle() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);

    // when
    PatchLogger logger = new PatchLogger(internalLogger);

    // then
    assertThat(logger.getResourceBundle()).isNull();
    assertThat(logger.getResourceBundleName()).isNull();
    verifyNoMoreInteractions(internalLogger);
  }

  private static Stream<Arguments> normalMethodArgs() {
    return Stream.of(
        Arguments.of(
            named("severe()", (BiConsumer<PatchLogger, String>) PatchLogger::severe),
            "ereves",
            InternalLogger.Level.ERROR),
        Arguments.of(
            named("warning()", (BiConsumer<PatchLogger, String>) PatchLogger::warning),
            "gninraw",
            InternalLogger.Level.WARN),
        Arguments.of(
            named("info()", (BiConsumer<PatchLogger, String>) PatchLogger::info),
            "ofni",
            InternalLogger.Level.INFO),
        Arguments.of(
            named("config()", (BiConsumer<PatchLogger, String>) PatchLogger::config),
            "gifnoc",
            InternalLogger.Level.INFO),
        Arguments.of(
            named("fine()", (BiConsumer<PatchLogger, String>) PatchLogger::fine),
            "enif",
            InternalLogger.Level.DEBUG),
        Arguments.of(
            named("finer()", (BiConsumer<PatchLogger, String>) PatchLogger::finer),
            "renif",
            InternalLogger.Level.TRACE),
        Arguments.of(
            named("finest()", (BiConsumer<PatchLogger, String>) PatchLogger::finest),
            "tsenif",
            InternalLogger.Level.TRACE));
  }

  private static Stream<Arguments> simpleLevelArgs() {
    return Stream.of(
        Arguments.of(SEVERE, "ereves", InternalLogger.Level.ERROR),
        Arguments.of(WARNING, "gninraw", InternalLogger.Level.WARN),
        Arguments.of(INFO, "ofni", InternalLogger.Level.INFO),
        Arguments.of(CONFIG, "gifnoc", InternalLogger.Level.DEBUG),
        Arguments.of(FINE, "enif", InternalLogger.Level.DEBUG),
        Arguments.of(FINER, "renif", InternalLogger.Level.TRACE),
        Arguments.of(FINEST, "tsenif", InternalLogger.Level.TRACE));
  }

  private static Stream<Arguments> formattedLevelArgs() {
    return Stream.of(
        Arguments.of(SEVERE, "ereves: {0}", "a", "ereves: a", InternalLogger.Level.ERROR),
        Arguments.of(WARNING, "gninraw: {0}", "b", "gninraw: b", InternalLogger.Level.WARN),
        Arguments.of(INFO, "ofni: {0}", "c", "ofni: c", InternalLogger.Level.INFO),
        Arguments.of(CONFIG, "gifnoc: {0}", "d", "gifnoc: d", InternalLogger.Level.DEBUG),
        Arguments.of(FINE, "enif: {0}", "e", "enif: e", InternalLogger.Level.DEBUG),
        Arguments.of(FINER, "renif: {0}", "f", "renif: f", InternalLogger.Level.TRACE),
        Arguments.of(FINEST, "tsenif: {0}", "g", "tsenif: g", InternalLogger.Level.TRACE));
  }

  private static Stream<Arguments> formattedArrayLevelArgs() {
    return Stream.of(
        Arguments.of(
            SEVERE,
            "ereves: {0},{1}",
            new Object[] {"a", "b"},
            "ereves: a,b",
            InternalLogger.Level.ERROR),
        Arguments.of(
            WARNING,
            "gninraw: {0},{1}",
            new Object[] {"b", "c"},
            "gninraw: b,c",
            InternalLogger.Level.WARN),
        Arguments.of(
            INFO, "ofni: {0},{1}", new Object[] {"c", "d"}, "ofni: c,d", InternalLogger.Level.INFO),
        Arguments.of(
            CONFIG,
            "gifnoc: {0},{1}",
            new Object[] {"d", "e"},
            "gifnoc: d,e",
            InternalLogger.Level.DEBUG),
        Arguments.of(
            FINE,
            "enif: {0},{1}",
            new Object[] {"e", "f"},
            "enif: e,f",
            InternalLogger.Level.DEBUG),
        Arguments.of(
            FINER,
            "renif: {0},{1}",
            new Object[] {"f", "g"},
            "renif: f,g",
            InternalLogger.Level.TRACE),
        Arguments.of(
            FINEST,
            "tsenif: {0},{1}",
            new Object[] {"g", "h"},
            "tsenif: g,h",
            InternalLogger.Level.TRACE));
  }

  private static Stream<Arguments> getLevelArgs() {
    return Stream.of(
        Arguments.of(InternalLogger.Level.ERROR, SEVERE),
        Arguments.of(InternalLogger.Level.WARN, WARNING),
        Arguments.of(InternalLogger.Level.INFO, CONFIG),
        Arguments.of(InternalLogger.Level.DEBUG, FINE),
        Arguments.of(InternalLogger.Level.TRACE, FINEST));
  }

  static class MethodSignature {
    String name;
    List<String> parameterTypes = new ArrayList<>();
    String returnType;
    int modifiers;

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof MethodSignature)) {
        return false;
      }
      MethodSignature other = (MethodSignature) obj;
      return Objects.equals(name, other.name)
          && Objects.equals(parameterTypes, other.parameterTypes)
          && Objects.equals(returnType, other.returnType)
          && Modifier.isStatic(modifiers) == Modifier.isStatic(other.modifiers);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, parameterTypes, returnType, Modifier.isStatic(modifiers));
    }

    @Override
    public String toString() {
      String params = parameterTypes.stream().reduce((a, b) -> a + ", " + b).orElse("");
      return Modifier.toString(modifiers) + " " + name + "(" + params + ")" + returnType;
    }
  }
}
