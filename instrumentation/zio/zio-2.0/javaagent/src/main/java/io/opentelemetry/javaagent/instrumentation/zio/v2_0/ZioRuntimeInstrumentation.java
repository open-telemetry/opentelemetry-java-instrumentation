/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import zio.Fiber;
import zio.Supervisor;

public class ZioRuntimeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("zio.Runtime$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("defaultSupervisor")), getClass().getName() + "$DefaultSupervisor");
  }

  public static final class DefaultSupervisor {

    private DefaultSupervisor() {}

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) Supervisor<?> supervisor) {
      @SuppressWarnings("rawtypes")
      VirtualField<Fiber.Runtime, FiberContext> virtualField =
          VirtualField.find(Fiber.Runtime.class, FiberContext.class);
      supervisor = supervisor.$plus$plus(new TracingSupervisor(virtualField));
    }
  }
}
