/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static net.bytebuddy.jar.asm.Type.getType;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeneratedVirtualFieldNamesTest {

  @ParameterizedTest
  @CsvSource({
    "'', io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$java$lang$Runnable$java$lang$String____",
    "test, io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$test$java$lang$Runnable$java$lang$String____"
  })
  void virtualFieldImplementation(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[][].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$java$lang$Runnable$java$lang$String__",
    "test, io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$test$java$lang$Runnable$java$lang$String__"
  })
  void accessorInterface(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getFieldAccessorInterfaceName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', __opentelemetryVirtualField$java$lang$Runnable$java$lang$String__",
    "test, __opentelemetryVirtualField$test$java$lang$Runnable$java$lang$String__"
  })
  void field(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getRealFieldName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', __set__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__",
    "test, __set__opentelemetryVirtualField$test$java$lang$Runnable$java$lang$String__"
  })
  void setter(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getRealSetterName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', __get__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__",
    "test, __get__opentelemetryVirtualField$test$java$lang$Runnable$java$lang$String__"
  })
  void getter(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getRealGetterName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }
}
