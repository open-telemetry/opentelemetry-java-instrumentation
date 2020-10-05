/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.TypeConstantAdjustment;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class AgentTransformers {

  private static final AgentBuilder.Transformer CONSTANT_ADJUSTER =
      new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(
            DynamicType.Builder<?> builder,
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule javaModule) {
          return builder.visit(TypeConstantAdjustment.INSTANCE);
        }
      };

  public static AgentBuilder.Transformer defaultTransformers() {
    return CONSTANT_ADJUSTER;
  }
}
