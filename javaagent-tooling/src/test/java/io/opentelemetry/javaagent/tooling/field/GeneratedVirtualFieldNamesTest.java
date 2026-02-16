/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static net.bytebuddy.jar.asm.Type.getType;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeneratedVirtualFieldNamesTest {

  @Test
  void virtualFieldImplementation() {
    assertThat(
            GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName(
                getType(Runnable.class).getClassName(), getType(String[][].class).getClassName()))
        .isEqualTo(
            "io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$java$lang$Runnable$java$lang$String____");
  }

  @Test
  void accessorInterface() {
    assertThat(
            GeneratedVirtualFieldNames.getFieldAccessorInterfaceName(
                getType(Runnable.class).getClassName(), getType(String[].class).getClassName()))
        .isEqualTo(
            "io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$java$lang$Runnable$java$lang$String__");
  }

  @Test
  void field() {
    assertThat(
            GeneratedVirtualFieldNames.getRealFieldName(
                getType(Runnable.class).getClassName(), getType(String[].class).getClassName()))
        .isEqualTo("__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__");
  }

  @Test
  void setter() {
    assertThat(
            GeneratedVirtualFieldNames.getRealSetterName(
                getType(Runnable.class).getClassName(), getType(String[].class).getClassName()))
        .isEqualTo("__set__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__");
  }

  @Test
  void getter() {
    assertThat(
            GeneratedVirtualFieldNames.getRealGetterName(
                getType(Runnable.class).getClassName(), getType(String[].class).getClassName()))
        .isEqualTo("__get__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__");
  }
}
