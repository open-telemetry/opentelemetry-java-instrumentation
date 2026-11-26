/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection.Framework;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LogPatternInjectionTest {
  private static final String FIELDS = "trace_id=%X{trace_id} span_id=%X{span_id}";

  @ParameterizedTest
  @EnumSource(Framework.class)
  void insertsBeforeMessageAndItsLiteralPrefix(Framework framework) {
    assertPattern(
        "[%d{yyyy-MM-dd HH:mm:ss.SSS}] %-5p [%t] - %m%n",
        "[%d{yyyy-MM-dd HH:mm:ss.SSS}] %-5p [%t] " + FIELDS + " - %m%n", framework);
    assertPattern("%d %-5p %m%n", "%d %-5p " + FIELDS + " %m%n", framework);
    assertPattern("prefix: %m%n", FIELDS + " prefix: %m%n", framework);
    assertPattern("%m%n", FIELDS + " %m%n", framework);
    assertPattern("%d %X{service.name} %m%n", "%d %X{service.name} " + FIELDS + " %m%n", framework);
  }

  @ParameterizedTest
  @EnumSource(Framework.class)
  void reusesExistingKeysAndIgnoresEscapedConverters(Framework framework) {
    assertPattern("%d %X{trace_id} %m", "%d %X{trace_id} span_id=%X{span_id} %m", framework);
    assertPattern("%d %%X{trace_id} %m", "%d " + FIELDS + " %%X{trace_id} %m", framework);
    assertPattern("%d %%m literal %m%n", "%d " + FIELDS + " %%m literal %m%n", framework);
    assertPattern("%d %X{trace_id} %X{span_id} %m", "%d %X{trace_id} %X{span_id} %m", framework);
  }

  @ParameterizedTest
  @ValueSource(strings = {"%msg", "%message", "%10.20msg"})
  void supportsMessageAliases(String message) {
    for (Framework framework : new Framework[] {Framework.LOGBACK, Framework.LOG4J2}) {
      assertPattern(
          "%d %-5p " + message + "%n", "%d %-5p " + FIELDS + " " + message + "%n", framework);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"%highlight", "%cyan"})
  void insertsWithinWholeLineLogbackWrappers(String wrapper) {
    assertPattern(
        wrapper + "(%d %-5p prefix: %msg%n)",
        wrapper + "(%d %-5p " + FIELDS + " prefix: %msg%n)",
        Framework.LOGBACK);
    assertPattern(
        "%d " + wrapper + "(%msg)%n",
        "%d " + FIELDS + " " + wrapper + "(%msg)%n",
        Framework.LOGBACK);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "highlight",
        "cyan",
        "style",
        "enc",
        "encode",
        "notEmpty",
        "varsNotEmpty",
        "variablesNotEmpty",
        "black",
        "blue",
        "green",
        "magenta",
        "red",
        "white",
        "yellow"
      })
  void supportsLog4j2Wrappers(String converter) {
    String options = converter.startsWith("max") ? "{100}" : "";
    assertPattern(
        "%" + converter + "{%d %-5p prefix: %m%n}" + options,
        "%" + converter + "{%d %-5p " + FIELDS + " prefix: %m%n}" + options,
        Framework.LOG4J2);
    assertPattern(
        "%d %" + converter + "{%X{trace_id}}" + options + " %m%n",
        "%d %" + converter + "{%X{trace_id}}" + options + " span_id=%X{span_id} %m%n",
        Framework.LOG4J2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"maxLen", "maxLength", "equals", "equalsIgnoreCase"})
  void recognizesNestedPatternsWithoutChangingTheirInput(String converter) {
    String options = converter.startsWith("max") ? "{20}" : "{ping}{pong}";
    assertPattern(
        "%d %" + converter + "{%p %m}" + options + "%n",
        "%d " + FIELDS + " %" + converter + "{%p %m}" + options + "%n",
        Framework.LOG4J2);
    assertPattern(
        "%" + converter + "{%X{trace_id}}" + options + " %m%n",
        "%" + converter + "{%X{trace_id}}" + options + " span_id=%X{span_id} %m%n",
        Framework.LOG4J2);
    assertPattern(
        "%cyan{%d %" + converter + "{%m}" + options + "}%n",
        "%cyan{%d " + FIELDS + " %" + converter + "{%m}" + options + "}%n",
        Framework.LOG4J2);
  }

  @ParameterizedTest
  @EnumSource(
      value = Framework.class,
      names = {"LOGBACK", "LOGBACK_LEGACY", "LOG4J2"})
  void insertsOutsideTruncationAndReplacement(Framework framework) {
    boolean log4j2 = framework == Framework.LOG4J2;
    String truncated = log4j2 ? "%.-20highlight{%p %m}" : "%.-20(%p %m)";
    String replacement = log4j2 ? "%replace{%p %m}{[0-9]}{X}" : "%replace(%p %m){'[0-9]','X'}";
    assertPattern("%d " + truncated, "%d " + FIELDS + " " + truncated, framework);
    assertPattern("%d " + replacement, "%d " + FIELDS + " " + replacement, framework);
    String padded = log4j2 ? "%-50highlight{%p %m}" : "%-50(%p %m)";
    String enhanced = log4j2 ? "%-50highlight{%p " + FIELDS + " %m}" : "%-50(%p " + FIELDS + " %m)";
    assertPattern(padded, enhanced, framework);
  }

  @Test
  void handlesNestedWrappersAndLiteralBraces() {
    assertPattern(
        "%highlight(%d %cyan(%msg)%n)",
        "%highlight(%d " + FIELDS + " %cyan(%msg)%n)", Framework.LOGBACK);
    assertPattern(
        "%highlight{%d %cyan{%m}%n}",
        "%highlight{%d " + FIELDS + " %cyan{%m}%n}", Framework.LOG4J2);
    assertPattern(
        "%replace(request={){'x','y'} %m%n",
        FIELDS + " %replace(request={){'x','y'} %m%n", Framework.LOGBACK);
    assertPattern("%d( %-5p %m)%n", "%d( %-5p " + FIELDS + " %m)%n", Framework.LOG4J2);
    assertPattern(
        "%d{\"yyyy-MM-dd '{'\"} %m", "%d{\"yyyy-MM-dd '{'\"} " + FIELDS + " %m", Framework.LOGBACK);
    assertPattern(
        "%replace(%d){':','%msg'} %m",
        "%replace(%d){':','%msg'} " + FIELDS + " %m", Framework.LOGBACK);
  }

  @Test
  void insertsBeforeWrappedLiteralMessagePrefixes() {
    assertPattern(
        "%d %cyan(prefix:) %msg%n", "%d " + FIELDS + " %cyan(prefix:) %msg%n", Framework.LOGBACK);
    assertPattern(
        "%d %cyan{prefix:} %m%n", "%d " + FIELDS + " %cyan{prefix:} %m%n", Framework.LOG4J2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"'trace_id'", " trace_id ", "trace_id,ignored", "trace_id:-none"})
  void recognizesNativeLogbackOptions(String option) {
    assertPattern(
        "%d %X{" + option + "} %m",
        "%d %X{" + option + "} span_id=%X{span_id} %m",
        Framework.LOGBACK);
  }

  @Test
  void respectsFrameworkSpecificMdcSemantics() {
    assertPattern("%d %X %m", "%d %X " + FIELDS + " %m", Framework.LOG4J1);
    for (Framework framework :
        new Framework[] {Framework.LOGBACK, Framework.LOG4J2, Framework.LOG4J1_WITH_MDC_MAP}) {
      assertPattern("%d %X %m", "%d %X %m", framework);
    }
    assertPattern(
        "%d %MDC{trace_id, span_id} %m", "%d %MDC{trace_id, span_id} %m", Framework.LOG4J2);
    for (String key : new String[] {" trace_id ", ",trace_id", ""}) {
      assertPattern(
          "%d %X{" + key + "} %m", "%d %X{" + key + "} " + FIELDS + " %m", Framework.LOG4J2);
    }
    assertPattern("%d %X{:-fallback} %m", "%d %X{:-fallback} " + FIELDS + " %m", Framework.LOGBACK);
    assertThat(
            LogPatternInjection.enhance(
                "%d %X{trace_id:-fallback} %m", Framework.LOGBACK_LEGACY, "trace_id", "span_id"))
        .isEqualTo("%d %X{trace_id:-fallback} " + FIELDS + " %m");
  }

  @ParameterizedTest
  @EnumSource(Framework.class)
  void leavesPatternsWithoutMessageUnchanged(Framework framework) {
    assertThat(LogPatternInjection.enhance(null, framework, "trace_id", "span_id")).isNull();
    for (String pattern : new String[] {"", "  ", "%d %p%n", "%d %%m%n"}) {
      assertPattern(pattern, pattern, framework);
    }
  }

  @Test
  void preservesEscapingAndDateOptionBoundaries() {
    assertPattern(
        "  timestamp=%d{HH:mm:ss} %m",
        "  timestamp=%d{HH:mm:ss} " + FIELDS + " %m", Framework.LOGBACK);
    assertPattern(
        "%replace(%d){':','%X{trace_id}'} %m",
        "%replace(%d){':','%X{trace_id}'} " + FIELDS + " %m", Framework.LOGBACK);
    assertPattern("%d %mdc{trace_id} %m", "%d " + FIELDS + " %mdc{trace_id} %m", Framework.LOG4J1);
    assertPattern("%d{HH:mm:ss\\} %m", "%d{HH:mm:ss\\} " + FIELDS + " %m", Framework.LOG4J2);
    for (Framework framework : Framework.values()) {
      String suffix = " \\%X{trace_id} %m";
      String expected =
          framework == Framework.LOGBACK || framework == Framework.LOGBACK_LEGACY
              ? "%d " + FIELDS + suffix
              : "%d \\%X{trace_id} span_id=%X{span_id} %m";
      assertPattern("%d" + suffix, expected, framework);
    }
    for (Framework framework : new Framework[] {Framework.LOG4J1, Framework.LOG4J1_WITH_MDC_MAP}) {
      assertPattern("%d{yyyy-MM-dd '{'} %m", "%d{yyyy-MM-dd '{'} " + FIELDS + " %m", framework);
    }
  }

  private static void assertPattern(String pattern, String expected, Framework framework) {
    String enhanced = LogPatternInjection.enhance(pattern, framework, "trace_id", "span_id");
    assertThat(enhanced).isEqualTo(expected);
    assertThat(LogPatternInjection.enhance(enhanced, framework, "trace_id", "span_id"))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(Framework.class)
  void injectsOnlyPortableKeys(Framework framework) {
    assertThat(LogPatternInjection.enhance("%m%n", framework, "custom,trace", "otel.span-id_1"))
        .isEqualTo("otel.span-id_1=%X{otel.span-id_1} %m%n");
    assertThat(LogPatternInjection.enhance("%m%n", framework, "traceId", "custom:-span"))
        .isEqualTo("traceId=%X{traceId} %m%n");
  }

  @Test
  void leavesThePatternUnchangedWhenNeitherKeyIsPortable() {
    assertThat(LogPatternInjection.enhance("%m%n", Framework.LOG4J2, "", "custom{span}"))
        .isEqualTo("%m%n");
  }
}
