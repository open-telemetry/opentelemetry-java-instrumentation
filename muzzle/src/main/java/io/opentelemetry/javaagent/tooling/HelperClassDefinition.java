/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import net.bytebuddy.dynamic.DynamicType;

public class HelperClassDefinition {

  private final String className;
  private final BytecodeWithUrl bytecode;
  private final InjectionMode injectionMode;

  private HelperClassDefinition(
      String className, BytecodeWithUrl bytecode, InjectionMode injectionMode) {
    this.className = className;
    this.bytecode = bytecode;
    this.injectionMode = injectionMode;
  }

  public static HelperClassDefinition create(
      String className, BytecodeWithUrl bytecode, InjectionMode injectionMode) {
    return new HelperClassDefinition(className, bytecode, injectionMode);
  }

  public static HelperClassDefinition create(
      DynamicType.Unloaded<?> type, InjectionMode injectionMode) {
    String name = type.getTypeDescription().getName();
    BytecodeWithUrl code = BytecodeWithUrl.create(type);
    return create(name, code, injectionMode);
  }

  public static HelperClassDefinition create(
      String className, ClassLoader copyFrom, InjectionMode injectionMode) {
    BytecodeWithUrl code = BytecodeWithUrl.create(className, copyFrom);
    return create(className, code, injectionMode);
  }

  public String getClassName() {
    return className;
  }

  public BytecodeWithUrl getBytecode() {
    return bytecode;
  }

  public InjectionMode getInjectionMode() {
    return injectionMode;
  }
}
