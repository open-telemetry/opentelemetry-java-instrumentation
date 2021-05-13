/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.TypeConstantAdjustment;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * This {@link AgentBuilder.Transformer} ensures that class files of a version previous to Java 5 do
 * not store class entries in the generated class's constant pool.
 *
 * @see ConstantAdjuster The ASM visitor that does the actual work.
 */
final class ConstantAdjuster implements AgentBuilder.Transformer {
  private static final ConstantAdjuster INSTANCE = new ConstantAdjuster();

  static AgentBuilder.Transformer instance() {
    return INSTANCE;
  }

  private ConstantAdjuster() {}

  @Override
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module) {
    return builder.visit(TypeConstantAdjustment.INSTANCE);
  }
}
