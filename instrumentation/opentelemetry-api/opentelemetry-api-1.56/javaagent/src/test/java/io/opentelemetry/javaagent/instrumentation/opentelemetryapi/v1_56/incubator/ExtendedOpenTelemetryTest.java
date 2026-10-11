/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExtendedOpenTelemetryTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private OpenTelemetry openTelemetry;

  @BeforeEach
  void setup() {
    openTelemetry = GlobalOpenTelemetry.get();
  }

  @Test
  void getConfig() {
    assertThat(openTelemetry).isInstanceOf(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties instrumentationConfig =
        ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider().getInstrumentationConfig();
    assertThat(instrumentationConfig).isNotNull();

    assertThat(instrumentationConfig.getStructured("java").getStructured("foo").getString("bar"))
        .isEqualTo("baz");
  }

  @Test
  void configTypePredicates() {
    DeclarativeConfigProperties instrumentationConfig =
        ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider().getInstrumentationConfig();
    DeclarativeConfigProperties foo =
        instrumentationConfig.getStructured("java").getStructured("foo");

    assertThat(isType(foo, "isString", "bar")).isTrue();
    assertThat(isType(foo, "isBoolean", "bool")).isTrue();
    assertThat(isType(foo, "isInt", "int")).isTrue();
    assertThat(isType(foo, "isLong", "long")).isTrue();
    assertThat(isType(foo, "isDouble", "double")).isTrue();
  }

  private static boolean isType(
      DeclarativeConfigProperties properties, String predicate, String name) {
    try {
      Method method = DeclarativeConfigProperties.class.getMethod(predicate, String.class);
      return (boolean) method.invoke(properties, name);
    } catch (NoSuchMethodException ignored) {
      Assumptions.assumeTrue(false);
      return false;
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }
}
