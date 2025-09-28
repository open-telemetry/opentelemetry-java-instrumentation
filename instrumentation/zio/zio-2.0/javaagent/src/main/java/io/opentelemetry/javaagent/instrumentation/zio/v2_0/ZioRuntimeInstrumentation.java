/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
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

  @SuppressWarnings("unused")
  public static final class DefaultSupervisor {

    private DefaultSupervisor() {}

    @Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.AssignReturned.ToReturned
    public static Object onExit(@Advice.Return Supervisor<?> supervisor) {
      return supervisor.$plus$plus(TracingSupervisor.INSTANCE);
    }
  }
}
