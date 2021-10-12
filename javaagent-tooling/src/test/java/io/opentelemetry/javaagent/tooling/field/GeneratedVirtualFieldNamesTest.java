/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static net.bytebuddy.jar.asm.Type.getType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GeneratedVirtualFieldNamesTest {

  @Test
  void virtualFieldImplementation() {
    assertEquals(
        "io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$java$lang$Runnable$java$lang$String____",
        GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName(
            getType(Runnable.class).getClassName(), getType(String[][].class).getClassName()));
  }

  @Test
  void accessorInterface() {
    assertEquals(
        "io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$java$lang$Runnable$java$lang$String__",
        GeneratedVirtualFieldNames.getFieldAccessorInterfaceName(
            getType(Runnable.class).getClassName(), getType(String[].class).getClassName()));
  }

  @Test
  void field() {
    assertEquals(
        "__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__",
        GeneratedVirtualFieldNames.getRealFieldName(
            getType(Runnable.class).getClassName(), getType(String[].class).getClassName()));
  }

  @Test
  void setter() {
    assertEquals(
        "set__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__",
        GeneratedVirtualFieldNames.getRealSetterName(
            getType(Runnable.class).getClassName(), getType(String[].class).getClassName()));
  }

  @Test
  void getter() {
    assertEquals(
        "get__opentelemetryVirtualField$java$lang$Runnable$java$lang$String__",
        GeneratedVirtualFieldNames.getRealGetterName(
            getType(Runnable.class).getClassName(), getType(String[].class).getClassName()));
  }
}
