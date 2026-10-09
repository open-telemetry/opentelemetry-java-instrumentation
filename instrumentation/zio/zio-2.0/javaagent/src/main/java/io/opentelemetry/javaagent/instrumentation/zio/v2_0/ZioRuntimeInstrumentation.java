/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import zio.Supervisor;

class ZioRuntimeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("zio.Runtime$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("defaultSupervisor").and(returns(named("zio.Supervisor"))),
        getClass().getName() + "$DefaultSupervisorAdvice");
  }

  @SuppressWarnings("unused")
  public static class DefaultSupervisorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Object onExit(@Advice.Return Supervisor<?> supervisor) {
      return supervisor.$plus$plus(TracingSupervisor.INSTANCE);
    }
  }
}
