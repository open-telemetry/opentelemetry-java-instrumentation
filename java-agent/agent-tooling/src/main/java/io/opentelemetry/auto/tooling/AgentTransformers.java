package io.opentelemetry.auto.tooling;

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
            final DynamicType.Builder<?> builder,
            final TypeDescription typeDescription,
            final ClassLoader classLoader,
            final JavaModule javaModule) {
          return builder.visit(TypeConstantAdjustment.INSTANCE);
        }
      };

  public static AgentBuilder.Transformer defaultTransformers() {
    return CONSTANT_ADJUSTER;
  }
}
