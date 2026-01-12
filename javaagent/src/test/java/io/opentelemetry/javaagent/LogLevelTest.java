/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jvmbootstraptest.LogLevelChecker;
import org.junit.jupiter.api.Test;

class LogLevelTest {

  /* Priority: io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel > opentelemetry.javaagent.debug > OTEL_JAVAAGENT_DEBUG
  1: INFO LOGS
  0: DEBUG Logs
   */

  @Test
  void otelJavaagentDebugFalse() throws Exception {
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {"-Dotel.javaagent.debug=false", "-Dotel.javaagent.enabled=false"},
            new String[0],
            Collections.emptyMap(),
            true);

    assertThat(exitCode).isOne();
  }

  @Test
  void slf4jDebugAndOtelJavaagentDebugIsFalse() throws Exception {
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {
              "-Dotel.javaagent.debug=false",
              "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug",
              "-Dotel.javaagent.enabled=false"
            },
            new String[0],
            Collections.emptyMap(),
            true);

    assertThat(exitCode).isZero();
  }

  @Test
  void otelJavaagentDebugIsFalseAndOtelJavaagentDebugIsTrue() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("OTEL_JAVAAGENT_DEBUG", "true");

    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {"-Dotel.javaagent.debug=false", "-Dotel.javaagent.enabled=false"},
            new String[0],
            env,
            true);

    assertThat(exitCode).isOne();
  }

  @Test
  void otelJavaagentDebugIsTrue() throws Exception {
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {"-Dotel.javaagent.debug=true", "-Dotel.javaagent.enabled=false"},
            new String[0],
            Collections.emptyMap(),
            true);

    assertThat(exitCode).isZero();
  }

  @Test
  void otelJavaagentDebugIsTrue2() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("OTEL_JAVAAGENT_DEBUG", "true");

    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {"-Dotel.javaagent.enabled=false"},
            new String[0],
            env,
            true);

    assertThat(exitCode).isZero();
  }

  @Test
  void otelJavaagentDebugIsTrueAndOtelJavaagentDebugIsFalse() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("OTEL_JAVAAGENT_DEBUG", "false");

    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {"-Dotel.javaagent.debug=true", "-Dotel.javaagent.enabled=false"},
            new String[0],
            env,
            true);

    assertThat(exitCode).isZero();
  }

  @Test
  void slf4jDebugAndOtelJavaagentDebugIsFalse2() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("OTEL_JAVAAGENT_DEBUG", "false");

    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {
              "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug",
              "-Dotel.javaagent.enabled=false"
            },
            new String[0],
            env,
            true);

    assertThat(exitCode).isZero();
  }

  @Test
  void slf4jInfoAndOtelJavaagentDebugIsTrue() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("OTEL_JAVAAGENT_DEBUG", "true");

    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            LogLevelChecker.class.getName(),
            new String[] {
              "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=info",
              "-Dotel.javaagent.enabled=false"
            },
            new String[0],
            env,
            true);

    assertThat(exitCode).isOne();
  }
}
