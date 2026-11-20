/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection.Framework;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LogPatternInjectionTest {
  @Test
  void scopesAliasRestorationAndRestoresNestedState() {
    String alias = "otel.pattern-injection.trace.Y3VzdG9tLHRyYWNl";
    LogPatternInjection.Injection injection =
        LogPatternInjection.prepare(
            "%X{" + alias + "} %m%n", Framework.LOGBACK, "custom,trace", "span_id");
    assertThat(LogPatternInjection.resolveMdcKey(alias + "_", Framework.LOGBACK))
        .isEqualTo(alias + "_");
    LogPatternInjection.Injection previous = LogPatternInjection.currentInjection().set(injection);
    try {
      assertThat(LogPatternInjection.resolveMdcKey(alias, Framework.LOGBACK)).isEqualTo(alias);
      assertThat(LogPatternInjection.resolveMdcKey(alias + "_", Framework.LOGBACK))
          .isEqualTo("custom,trace");
      LogPatternInjection.Injection nested =
          LogPatternInjection.currentInjection()
              .set(
                  LogPatternInjection.prepare(
                      "%X{" + alias + "_}", Framework.LOGBACK, "custom,trace", "span_id"));
      try {
        assertThat(LogPatternInjection.resolveMdcKey(alias + "_", Framework.LOGBACK))
            .isEqualTo(alias + "_");
      } finally {
        LogPatternInjection.currentInjection().restore(nested);
      }
      assertThat(LogPatternInjection.resolveMdcKey(alias + "_", Framework.LOGBACK))
          .isEqualTo("custom,trace");
    } finally {
      LogPatternInjection.currentInjection().restore(previous);
    }
    assertThat(LogPatternInjection.resolveMdcKey(alias + "_", Framework.LOGBACK))
        .isEqualTo(alias + "_");
  }

  @Test
  void doesNotRestoreAliasesWhenNothingWasInjected() {
    String alias = "otel.pattern-injection.trace.Y3VzdG9tLHRyYWNl";
    LogPatternInjection.Injection previous =
        LogPatternInjection.currentInjection()
            .set(LogPatternInjection.prepare(null, Framework.LOGBACK, "custom,trace", "span_id"));
    try {
      assertThat(LogPatternInjection.resolveMdcKey(alias, Framework.LOGBACK)).isEqualTo(alias);
    } finally {
      LogPatternInjection.currentInjection().restore(previous);
    }
  }

  @Test
  void avoidsApplicationAliasCollisions() {
    String alias = "otel.pattern-injection.trace.Y3VzdG9tLHRyYWNl";
    String pattern = "%X{" + alias + "} %X{" + alias + "_} %m%n";
    LogPatternInjection.Injection injection =
        LogPatternInjection.prepare(pattern, Framework.LOGBACK, "custom,trace", "span_id");
    assertThat(injection.getPattern())
        .contains(pattern.substring(0, pattern.indexOf("%m")))
        .contains("custom,trace=%X{" + alias + "__}");
    assertThat(
            LogPatternInjection.prepare(
                    "%X{" + alias + "}", Framework.LOGBACK, "custom,trace", "span_id")
                .getPattern())
        .isEqualTo("%X{" + alias + "}");
  }

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
  @ValueSource(strings = {"%highlight", "%replace"})
  void insertsWithinWholeLineLogbackWrappers(String wrapper) {
    String options = wrapper.equals("%replace") ? "{'unused','replacement'}" : "";
    assertPattern(
        wrapper + "(%d %-5p prefix: %msg%n)" + options,
        wrapper + "(%d %-5p " + FIELDS + " prefix: %msg%n)" + options,
        Framework.LOGBACK);
    assertPattern(
        "%d " + wrapper + "(%msg)" + options + "%n",
        "%d " + FIELDS + " " + wrapper + "(%msg)" + options + "%n",
        Framework.LOGBACK);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "highlight",
        "cyan",
        "style",
        "replace",
        "enc",
        "encode",
        "notEmpty",
        "varsNotEmpty",
        "variablesNotEmpty",
        "maxLen",
        "maxLength",
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
    String legacy =
        LogPatternInjection.enhance(
            "%d %X{custom:-trace} %m", Framework.LOGBACK_LEGACY, "custom:-trace", "span_id");
    assertThat(legacy).isEqualTo("%d %X{custom:-trace} span_id=%X{span_id} %m");
    assertThat(
            LogPatternInjection.enhance(
                "%d %X{custom:-trace} %m", Framework.LOGBACK, "custom:-trace", "span_id"))
        .contains("custom:-trace=%X{otel.pattern-injection.trace.Y3VzdG9tOi10cmFjZQ}");
    assertThat(
            LogPatternInjection.enhance(
                "%d %X{'custom,trace'} %m", Framework.LOGBACK, "custom,trace", "span_id"))
        .isEqualTo("%d %X{'custom,trace'} span_id=%X{span_id} %m");
    String escaped = "%d %X{'custom,\\'\"trace'} %m";
    assertThat(
            LogPatternInjection.enhance(escaped, Framework.LOGBACK, "custom,\\'\"trace", "span_id"))
        .isEqualTo("%d %X{'custom,\\'\"trace'} span_id=%X{span_id} %m");
    assertThat(
            LogPatternInjection.enhance(escaped, Framework.LOGBACK, "custom,'\"trace", "span_id"))
        .contains("custom,'\"trace=%X{otel.pattern-injection.trace.Y3VzdG9tLCcidHJhY2U}");
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
      String enhanced =
          LogPatternInjection.enhance("%d %m%n", framework, "custom}trace", "custom%span");
      assertThat(enhanced)
          .isEqualTo(
              "%d custom}trace=%X{otel.pattern-injection.log4j1.trace.Y3VzdG9tfXRyYWNl} custom%%span=%X{otel.pattern-injection.log4j1.span.Y3VzdG9tJXNwYW4} %m%n");
      assertThat(LogPatternInjection.enhance(enhanced, framework, "custom}trace", "custom%span"))
          .isEqualTo(enhanced);
    }
    assertThat(
            LogPatternInjection.enhance(
                "%d %X{custom,trace} %m", Framework.LOG4J2, "custom,trace", "span_id"))
        .isEqualTo(
            "%d %X{custom,trace} custom,trace=%X{otel.pattern-injection.log4j2.trace.Y3VzdG9tLHRyYWNl} span_id=%X{span_id} %m");
  }

  private static void assertPattern(String pattern, String expected, Framework framework) {
    String enhanced = LogPatternInjection.enhance(pattern, framework, "trace_id", "span_id");
    assertThat(enhanced).isEqualTo(expected);
    assertThat(LogPatternInjection.enhance(enhanced, framework, "trace_id", "span_id"))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("complexLogbackKeys")
  void preservesComplexLogbackKeys(String traceKey, String label, String option) {
    String enhanced =
        LogPatternInjection.enhance("%d %m%n", Framework.LOGBACK, traceKey, "span_id");
    assertThat(enhanced).isEqualTo("%d " + label + "=%X{" + option + "} span_id=%X{span_id} %m%n");
    assertThat(LogPatternInjection.enhance(enhanced, Framework.LOGBACK, traceKey, "span_id"))
        .isEqualTo(enhanced);
  }

  private static Stream<Arguments> complexLogbackKeys() {
    return Stream.of(
        argumentSet(
            "comma",
            "custom,trace",
            "custom,trace",
            "otel.pattern-injection.trace.Y3VzdG9tLHRyYWNl"),
        argumentSet(
            "single quote",
            "custom,'trace",
            "custom,'trace",
            "otel.pattern-injection.trace.Y3VzdG9tLCd0cmFjZQ"),
        argumentSet(
            "double quote",
            "custom,\"trace",
            "custom,\"trace",
            "otel.pattern-injection.trace.Y3VzdG9tLCJ0cmFjZQ"),
        argumentSet(
            "both quotes",
            "custom,'\"trace",
            "custom,'\"trace",
            "otel.pattern-injection.trace.Y3VzdG9tLCcidHJhY2U"),
        argumentSet(
            "backslashes",
            "custom,\\trace\\",
            "custom,\\\\trace\\\\",
            "otel.pattern-injection.trace.Y3VzdG9tLFx0cmFjZVw"),
        argumentSet(
            "literal pattern syntax",
            "custom,%trace()",
            "custom,\\%trace\\(\\)",
            "otel.pattern-injection.trace.Y3VzdG9tLCV0cmFjZSgp"),
        argumentSet(
            "native default delimiter",
            "custom:-trace",
            "custom:-trace",
            "otel.pattern-injection.trace.Y3VzdG9tOi10cmFjZQ"),
        argumentSet(
            "braces",
            "custom{trace}",
            "custom{trace}",
            "otel.pattern-injection.trace.Y3VzdG9te3RyYWNlfQ"),
        argumentSet(
            "whitespace",
            " custom trace ",
            " custom trace ",
            "otel.pattern-injection.trace.IGN1c3RvbSB0cmFjZSA"));
  }

  @ParameterizedTest
  @MethodSource("complexLog4j2Keys")
  void preservesComplexLog4j2Keys(String traceKey, String label, String option) {
    String enhanced = LogPatternInjection.enhance("%d %m%n", Framework.LOG4J2, traceKey, "span_id");
    assertThat(enhanced).isEqualTo("%d " + label + "=%X{" + option + "} span_id=%X{span_id} %m%n");
    assertThat(LogPatternInjection.enhance(enhanced, Framework.LOG4J2, traceKey, "span_id"))
        .isEqualTo(enhanced);
  }

  private static Stream<Arguments> complexLog4j2Keys() {
    return Stream.of(
        argumentSet(
            "comma",
            "custom,trace",
            "custom,trace",
            "otel.pattern-injection.log4j2.trace.Y3VzdG9tLHRyYWNl"),
        argumentSet(
            "literal pattern syntax",
            "custom,%trace{}",
            "custom,%%trace{}",
            "otel.pattern-injection.log4j2.trace.Y3VzdG9tLCV0cmFjZXt9"),
        argumentSet(
            "quotes and backslash",
            "custom,'\"trace\\",
            "custom,'\"trace\\",
            "otel.pattern-injection.log4j2.trace.Y3VzdG9tLCcidHJhY2Vc"),
        argumentSet(
            "whitespace",
            " custom trace ",
            " custom trace ",
            "otel.pattern-injection.log4j2.trace.IGN1c3RvbSB0cmFjZSA"));
  }
}
