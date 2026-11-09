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
  private static final String FIELDS =
      "service.name=%X{service.name} trace_id=%X{trace_id} span_id=%X{span_id}";

  @ParameterizedTest
  @EnumSource(Framework.class)
  void preservesDateAndIsIdempotent(Framework framework) {
    String pattern = "[%d{yyyy-MM-dd HH:mm:ss.SSS}] %-5p %m%n";
    String enhanced = LogPatternInjection.enhance(pattern, framework, "trace_id", "span_id");
    assertThat(enhanced).isEqualTo("[%d{yyyy-MM-dd HH:mm:ss.SSS}] " + FIELDS + " %-5p %m%n");
    assertThat(LogPatternInjection.enhance(enhanced, framework, "trace_id", "span_id"))
        .isEqualTo(enhanced);
  }

  @ParameterizedTest
  @EnumSource(Framework.class)
  void literalKeysAndEscapedConvertersDoNotSuppress(Framework framework) {
    String pattern = "%d trace_id %%X{span_id} service.name %m%n";
    assertThat(LogPatternInjection.enhance(pattern, framework, "trace_id", "span_id"))
        .isEqualTo("%d " + FIELDS + " trace_id %%X{span_id} service.name %m%n");
    assertThat(LogPatternInjection.enhance("%d %X{trace_id} %m", framework, "trace_id", "span_id"))
        .isEqualTo("%d service.name=%X{service.name} span_id=%X{span_id} %X{trace_id} %m");
  }

  @Test
  void frameworkSpecificSyntaxAndWrappers() {
    assertThat(
            LogPatternInjection.enhance(
                "%clr(%d{HH:mm:ss}){blue} %mdc{trace_id:-none} %m",
                Framework.LOGBACK, "trace_id", "span_id"))
        .isEqualTo(
            "%clr(%d{HH:mm:ss}){blue} service.name=%X{service.name} span_id=%X{span_id}"
                + " %mdc{trace_id:-none} %m");
    assertThat(LogPatternInjection.enhance("%d %X %m", Framework.LOGBACK, "trace_id", "span_id"))
        .isEqualTo("%d %X %m");
    assertThat(
            LogPatternInjection.enhance(
                "%d %MDC{service.name, trace_id, span_id} %m",
                Framework.LOG4J2, "trace_id", "span_id"))
        .isEqualTo("%d %MDC{service.name, trace_id, span_id} %m");
    assertThat(
            LogPatternInjection.enhance(
                "%d %mdc{trace_id} %m", Framework.LOG4J1, "trace_id", "span_id"))
        .isEqualTo("%d " + FIELDS + " %mdc{trace_id} %m");
    assertThat(
            LogPatternInjection.enhance(
                "%d %X{trace_id} %m", Framework.LOG4J2, "custom_trace", "custom_span"))
        .contains("custom_trace=%X{custom_trace}", "custom_span=%X{custom_span}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"'trace_id'", " trace_id ", "trace_id,ignored"})
  void recognizesNativeLogbackOptions(String option) {
    assertThat(
            LogPatternInjection.enhance(
                "%d %X{" + option + "} %m", Framework.LOGBACK, "trace_id", "span_id"))
        .isEqualTo("%d service.name=%X{service.name} span_id=%X{span_id} %X{" + option + "} %m");
  }

  @Test
  void preservesQuotedDateOptionsAndBraces() {
    String field = "%d{\"yyyy-MM-dd '{'\"}";
    assertThat(LogPatternInjection.enhance(field + " %m", Framework.LOGBACK, "trace_id", "span_id"))
        .isEqualTo(field + " " + FIELDS + " %m");
  }

  @Test
  void wrapperOptionTextDoesNotCountAsAnMdcConverter() {
    String field = "%replace(%d{HH:mm:ss}){':', '%X{trace_id}'}";
    assertThat(LogPatternInjection.enhance(field + " %m", Framework.LOGBACK, "trace_id", "span_id"))
        .isEqualTo(field + " " + FIELDS + " %m");
  }

  @Test
  void distinguishesEarlyAndLaterLog4jWholeMdc() {
    assertThat(LogPatternInjection.enhance("%d %X %m", Framework.LOG4J1, "trace_id", "span_id"))
        .isEqualTo("%d " + FIELDS + " %X %m");
    assertThat(
            LogPatternInjection.enhance(
                "%d %X %m", Framework.LOG4J1_WITH_MDC_MAP, "trace_id", "span_id"))
        .isEqualTo("%d %X %m");
  }

  @Test
  void preservesLiteralDatePrefixAndLeadingWhitespace() {
    assertThat(
            LogPatternInjection.enhance(
                "  timestamp=%d{HH:mm:ss} %m", Framework.LOGBACK, "trace_id", "span_id"))
        .isEqualTo("  timestamp=%d{HH:mm:ss} " + FIELDS + " %m");
  }

  @ParameterizedTest
  @EnumSource(
      value = Framework.class,
      names = {"LOG4J1", "LOG4J1_WITH_MDC_MAP"})
  void log4j1DateOptionsEndAtTheFirstClosingBrace(Framework framework) {
    String field = "%d{yyyy-MM-dd '{'}";
    assertThat(LogPatternInjection.enhance(field + " %m", framework, "trace_id", "span_id"))
        .isEqualTo(field + " " + FIELDS + " %m");
  }

  @Test
  void log4j2BackslashDoesNotEscapeAnOptionBoundary() {
    String field = "%d{HH:mm:ss\\}";
    assertThat(LogPatternInjection.enhance(field + " %m", Framework.LOG4J2, "trace_id", "span_id"))
        .isEqualTo(field + " " + FIELDS + " %m");
  }

  @ParameterizedTest
  @EnumSource(Framework.class)
  void respectsFrameworkSpecificPercentEscaping(Framework framework) {
    String suffix = " \\%X{trace_id} %m";
    String fields =
        framework == Framework.LOGBACK
            ? FIELDS
            : "service.name=%X{service.name} span_id=%X{span_id}";
    assertThat(LogPatternInjection.enhance("%d" + suffix, framework, "trace_id", "span_id"))
        .isEqualTo("%d " + fields + suffix);
  }

  @Test
  void emptyPatternsAreUnchanged() {
    assertThat(LogPatternInjection.enhance(null, Framework.LOGBACK, "trace_id", "span_id"))
        .isNull();
    assertThat(LogPatternInjection.enhance("", Framework.LOGBACK, "trace_id", "span_id")).isEmpty();
  }
}
