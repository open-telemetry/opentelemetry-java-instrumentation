/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
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
    for (Method method : java.util.logging.Logger.class.getMethods()) {
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

  @Test
  void testNormalMethods() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.severe("ereves");
    logger.warning("gninraw");
    logger.info("ofni");
    logger.config("gifnoc");
    logger.fine("enif");
    logger.finer("renif");
    logger.finest("tsenif");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testParameterizedLevelMethodsWithNoParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.log(Level.SEVERE, "ereves");
    logger.log(Level.WARNING, "gninraw");
    logger.log(Level.INFO, "ofni");
    logger.log(Level.CONFIG, "gifnoc");
    logger.log(Level.FINE, "enif");
    logger.log(Level.FINER, "renif");
    logger.log(Level.FINEST, "tsenif");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testParameterizedLevelMethodsWithSingleParam() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.log(Level.SEVERE, "ereves: {0}", "a");
    logger.log(Level.WARNING, "gninraw: {0}", "b");
    logger.log(Level.INFO, "ofni: {0}", "c");
    logger.log(Level.CONFIG, "gifnoc: {0}", "d");
    logger.log(Level.FINE, "enif: {0}", "e");
    logger.log(Level.FINER, "renif: {0}", "f");
    logger.log(Level.FINEST, "tsenif: {0}", "g");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testParameterizedLevelMethodsWithArrayOfParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.log(Level.SEVERE, "ereves: {0},{1}", new Object[] {"a", "b"});
    logger.log(Level.WARNING, "gninraw: {0},{1}", new Object[] {"b", "c"});
    logger.log(Level.INFO, "ofni: {0},{1}", new Object[] {"c", "d"});
    logger.log(Level.CONFIG, "gifnoc: {0},{1}", new Object[] {"d", "e"});
    logger.log(Level.FINE, "enif: {0},{1}", new Object[] {"e", "f"});
    logger.log(Level.FINER, "renif: {0},{1}", new Object[] {"f", "g"});
    logger.log(Level.FINEST, "tsenif: {0},{1}", new Object[] {"g", "h"});

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d,e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e,f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f,g", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g,h", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testParameterizedLevelMethodsWithThrowable() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable a = new Throwable();
    Throwable b = new Throwable();
    Throwable c = new Throwable();
    Throwable d = new Throwable();
    Throwable e = new Throwable();
    Throwable f = new Throwable();
    Throwable g = new Throwable();

    // when
    logger.log(Level.SEVERE, "ereves", a);
    logger.log(Level.WARNING, "gninraw", b);
    logger.log(Level.INFO, "ofni", c);
    logger.log(Level.CONFIG, "gifnoc", d);
    logger.log(Level.FINE, "enif", e);
    logger.log(Level.FINER, "renif", f);
    logger.log(Level.FINEST, "tsenif", g);

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", a);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", b);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", c);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", d);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", e);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", f);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", g);
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
    assertThat(logger.isLoggable(Level.SEVERE)).isTrue();
    assertThat(logger.isLoggable(Level.WARNING)).isTrue();
    assertThat(logger.isLoggable(Level.INFO)).isTrue();
    assertThat(logger.isLoggable(Level.CONFIG)).isTrue();
    assertThat(logger.isLoggable(Level.FINE)).isTrue();
    assertThat(logger.isLoggable(Level.FINER)).isTrue();
    assertThat(logger.isLoggable(Level.FINEST)).isTrue();
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
    assertThat(logger.isLoggable(Level.SEVERE)).isTrue();
    assertThat(logger.isLoggable(Level.WARNING)).isTrue();
    assertThat(logger.isLoggable(Level.INFO)).isFalse();
    assertThat(logger.isLoggable(Level.CONFIG)).isFalse();
    assertThat(logger.isLoggable(Level.FINE)).isFalse();
    assertThat(logger.isLoggable(Level.FINER)).isFalse();
    assertThat(logger.isLoggable(Level.FINEST)).isFalse();
  }

  @Test
  void testIsLoggableNone() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);

    // when
    PatchLogger logger = new PatchLogger(internalLogger);

    // then
    assertThat(logger.isLoggable(Level.SEVERE)).isFalse();
    assertThat(logger.isLoggable(Level.WARNING)).isFalse();
    assertThat(logger.isLoggable(Level.INFO)).isFalse();
    assertThat(logger.isLoggable(Level.CONFIG)).isFalse();
    assertThat(logger.isLoggable(Level.FINE)).isFalse();
    assertThat(logger.isLoggable(Level.FINER)).isFalse();
    assertThat(logger.isLoggable(Level.FINEST)).isFalse();
  }

  @Test
  void testGetLevelSevere() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(InternalLogger.Level.ERROR)).thenReturn(true);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(Level.SEVERE);
  }

  @Test
  void testGetLevelWarning() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(InternalLogger.Level.WARN)).thenReturn(true);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(Level.WARNING);
  }

  @Test
  void testGetLevelConfig() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(InternalLogger.Level.INFO)).thenReturn(true);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(Level.CONFIG);
  }

  @Test
  void testGetLevelFine() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(InternalLogger.Level.DEBUG)).thenReturn(true);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(Level.FINE);
  }

  @Test
  void testGetLevelFinest() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(InternalLogger.Level.TRACE)).thenReturn(true);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(Level.FINEST);
  }

  @Test
  void testGetLevelOff() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    // when
    PatchLogger logger = new PatchLogger(internalLogger);
    // then
    assertThat(logger.getLevel()).isEqualTo(Level.OFF);
  }

  @Test
  void testLogpParameterizedLevelMethodsWithNoParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logp(Level.SEVERE, null, null, "ereves");
    logger.logp(Level.WARNING, null, null, "gninraw");
    logger.logp(Level.INFO, null, null, "ofni");
    logger.logp(Level.CONFIG, null, null, "gifnoc");
    logger.logp(Level.FINE, null, null, "enif");
    logger.logp(Level.FINER, null, null, "renif");
    logger.logp(Level.FINEST, null, null, "tsenif");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogpParameterizedLevelMethodsWithSingleParam() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logp(Level.SEVERE, null, null, "ereves: {0}", "a");
    logger.logp(Level.WARNING, null, null, "gninraw: {0}", "b");
    logger.logp(Level.INFO, null, null, "ofni: {0}", "c");
    logger.logp(Level.CONFIG, null, null, "gifnoc: {0}", "d");
    logger.logp(Level.FINE, null, null, "enif: {0}", "e");
    logger.logp(Level.FINER, null, null, "renif: {0}", "f");
    logger.logp(Level.FINEST, null, null, "tsenif: {0}", "g");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogpParameterizedLevelMethodsWithArrayOfParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logp(Level.SEVERE, null, null, "ereves: {0},{1}", new Object[] {"a", "b"});
    logger.logp(Level.WARNING, null, null, "gninraw: {0},{1}", new Object[] {"b", "c"});
    logger.logp(Level.INFO, null, null, "ofni: {0},{1}", new Object[] {"c", "d"});
    logger.logp(Level.CONFIG, null, null, "gifnoc: {0},{1}", new Object[] {"d", "e"});
    logger.logp(Level.FINE, null, null, "enif: {0},{1}", new Object[] {"e", "f"});
    logger.logp(Level.FINER, null, null, "renif: {0},{1}", new Object[] {"f", "g"});
    logger.logp(Level.FINEST, null, null, "tsenif: {0},{1}", new Object[] {"g", "h"});

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d,e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e,f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f,g", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g,h", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogpParameterizedLevelMethodsWithThrowable() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable a = new Throwable();
    Throwable b = new Throwable();
    Throwable c = new Throwable();
    Throwable d = new Throwable();
    Throwable e = new Throwable();
    Throwable f = new Throwable();
    Throwable g = new Throwable();

    // when
    logger.logp(Level.SEVERE, null, null, "ereves", a);
    logger.logp(Level.WARNING, null, null, "gninraw", b);
    logger.logp(Level.INFO, null, null, "ofni", c);
    logger.logp(Level.CONFIG, null, null, "gifnoc", d);
    logger.logp(Level.FINE, null, null, "enif", e);
    logger.logp(Level.FINER, null, null, "renif", f);
    logger.logp(Level.FINEST, null, null, "tsenif", g);

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", a);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", b);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", c);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", d);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", e);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", f);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", g);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithNoParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logrb(Level.SEVERE, null, null, null, "ereves");
    logger.logrb(Level.WARNING, null, null, null, "gninraw");
    logger.logrb(Level.INFO, null, null, null, "ofni");
    logger.logrb(Level.CONFIG, null, null, null, "gifnoc");
    logger.logrb(Level.FINE, null, null, null, "enif");
    logger.logrb(Level.FINER, null, null, null, "renif");
    logger.logrb(Level.FINEST, null, null, null, "tsenif");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", null);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithSingleParam() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logrb(Level.SEVERE, null, null, null, "ereves: {0}", "a");
    logger.logrb(Level.WARNING, null, null, null, "gninraw: {0}", "b");
    logger.logrb(Level.INFO, null, null, null, "ofni: {0}", "c");
    logger.logrb(Level.CONFIG, null, null, null, "gifnoc: {0}", "d");
    logger.logrb(Level.FINE, null, null, null, "enif: {0}", "e");
    logger.logrb(Level.FINER, null, null, null, "renif: {0}", "f");
    logger.logrb(Level.FINEST, null, null, null, "tsenif: {0}", "g");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithArrayOfParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logrb(
        Level.SEVERE, null, null, (String) null, "ereves: {0},{1}", new Object[] {"a", "b"});
    logger.logrb(
        Level.WARNING, null, null, (String) null, "gninraw: {0},{1}", new Object[] {"b", "c"});
    logger.logrb(Level.INFO, null, null, (String) null, "ofni: {0},{1}", new Object[] {"c", "d"});
    logger.logrb(
        Level.CONFIG, null, null, (String) null, "gifnoc: {0},{1}", new Object[] {"d", "e"});
    logger.logrb(Level.FINE, null, null, (String) null, "enif: {0},{1}", new Object[] {"e", "f"});
    logger.logrb(Level.FINER, null, null, (String) null, "renif: {0},{1}", new Object[] {"f", "g"});
    logger.logrb(
        Level.FINEST, null, null, (String) null, "tsenif: {0},{1}", new Object[] {"g", "h"});

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d,e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e,f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f,g", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g,h", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithVarArgsOfParams() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    when(internalLogger.isLoggable(any())).thenReturn(true);
    PatchLogger logger = new PatchLogger(internalLogger);

    // when
    logger.logrb(Level.SEVERE, (String) null, null, null, "ereves: {0},{1}", "a", "b");
    logger.logrb(Level.WARNING, (String) null, null, null, "gninraw: {0},{1}", "b", "c");
    logger.logrb(Level.INFO, (String) null, null, null, "ofni: {0},{1}", "c", "d");
    logger.logrb(Level.CONFIG, (String) null, null, null, "gifnoc: {0},{1}", "d", "e");
    logger.logrb(Level.FINE, (String) null, null, null, "enif: {0},{1}", "e", "f");
    logger.logrb(Level.FINER, (String) null, null, null, "renif: {0},{1}", "f", "g");
    logger.logrb(Level.FINEST, (String) null, null, null, "tsenif: {0},{1}", "g", "h");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d,e", null);
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
    logger.logrb(Level.SEVERE, (ResourceBundle) null, "ereves: {0},{1}", "a", "b");
    logger.logrb(Level.WARNING, (ResourceBundle) null, "gninraw: {0},{1}", "b", "c");
    logger.logrb(Level.INFO, (ResourceBundle) null, "ofni: {0},{1}", "c", "d");
    logger.logrb(Level.CONFIG, (ResourceBundle) null, "gifnoc: {0},{1}", "d", "e");
    logger.logrb(Level.FINE, (ResourceBundle) null, "enif: {0},{1}", "e", "f");
    logger.logrb(Level.FINER, (ResourceBundle) null, "renif: {0},{1}", "f", "g");
    logger.logrb(Level.FINEST, (ResourceBundle) null, "tsenif: {0},{1}", "g", "h");

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.ERROR);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves: a,b", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.WARN);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw: b,c", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni: c,d", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.INFO);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc: d,e", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.DEBUG);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif: e,f", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif: f,g", null);
    inOrder.verify(internalLogger).isLoggable(InternalLogger.Level.TRACE);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif: g,h", null);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithThrowable() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable a = new Throwable();
    Throwable b = new Throwable();
    Throwable c = new Throwable();
    Throwable d = new Throwable();
    Throwable e = new Throwable();
    Throwable f = new Throwable();
    Throwable g = new Throwable();

    // when
    logger.logrb(Level.SEVERE, null, null, (String) null, "ereves", a);
    logger.logrb(Level.WARNING, null, null, (String) null, "gninraw", b);
    logger.logrb(Level.INFO, null, null, (String) null, "ofni", c);
    logger.logrb(Level.CONFIG, null, null, (String) null, "gifnoc", d);
    logger.logrb(Level.FINE, null, null, (String) null, "enif", e);
    logger.logrb(Level.FINER, null, null, (String) null, "renif", f);
    logger.logrb(Level.FINEST, null, null, (String) null, "tsenif", g);

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", a);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", b);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", c);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", d);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", e);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", f);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", g);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithThrowable2() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable a = new Throwable();
    Throwable b = new Throwable();
    Throwable c = new Throwable();
    Throwable d = new Throwable();
    Throwable e = new Throwable();
    Throwable f = new Throwable();
    Throwable g = new Throwable();

    // when
    logger.logrb(Level.SEVERE, null, null, (ResourceBundle) null, "ereves", a);
    logger.logrb(Level.WARNING, null, null, (ResourceBundle) null, "gninraw", b);
    logger.logrb(Level.INFO, null, null, (ResourceBundle) null, "ofni", c);
    logger.logrb(Level.CONFIG, null, null, (ResourceBundle) null, "gifnoc", d);
    logger.logrb(Level.FINE, null, null, (ResourceBundle) null, "enif", e);
    logger.logrb(Level.FINER, null, null, (ResourceBundle) null, "renif", f);
    logger.logrb(Level.FINEST, null, null, (ResourceBundle) null, "tsenif", g);

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", a);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", b);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", c);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", d);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", e);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", f);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", g);
    verifyNoMoreInteractions(internalLogger);
  }

  @Test
  void testLogrbParameterizedLevelMethodsWithResourceBundleObjectAndThrowable() {
    // given
    InternalLogger internalLogger = mock(InternalLogger.class);
    PatchLogger logger = new PatchLogger(internalLogger);
    Throwable a = new Throwable();
    Throwable b = new Throwable();
    Throwable c = new Throwable();
    Throwable d = new Throwable();
    Throwable e = new Throwable();
    Throwable f = new Throwable();
    Throwable g = new Throwable();

    // when
    logger.logrb(Level.SEVERE, null, null, (ResourceBundle) null, "ereves", a);
    logger.logrb(Level.WARNING, null, null, (ResourceBundle) null, "gninraw", b);
    logger.logrb(Level.INFO, null, null, (ResourceBundle) null, "ofni", c);
    logger.logrb(Level.CONFIG, null, null, (ResourceBundle) null, "gifnoc", d);
    logger.logrb(Level.FINE, null, null, (ResourceBundle) null, "enif", e);
    logger.logrb(Level.FINER, null, null, (ResourceBundle) null, "renif", f);
    logger.logrb(Level.FINEST, null, null, (ResourceBundle) null, "tsenif", g);

    // then
    InOrder inOrder = Mockito.inOrder(internalLogger);
    inOrder.verify(internalLogger).log(InternalLogger.Level.ERROR, "ereves", a);
    inOrder.verify(internalLogger).log(InternalLogger.Level.WARN, "gninraw", b);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "ofni", c);
    inOrder.verify(internalLogger).log(InternalLogger.Level.INFO, "gifnoc", d);
    inOrder.verify(internalLogger).log(InternalLogger.Level.DEBUG, "enif", e);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "renif", f);
    inOrder.verify(internalLogger).log(InternalLogger.Level.TRACE, "tsenif", g);
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
