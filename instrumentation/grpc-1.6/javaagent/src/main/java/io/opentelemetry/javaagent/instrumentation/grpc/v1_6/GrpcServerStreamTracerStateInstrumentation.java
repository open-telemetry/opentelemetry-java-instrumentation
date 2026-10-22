/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.grpc.Context;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcServerStreamTracerState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GrpcServerStreamTracerStateInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "application.io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcServerStreamTracerState");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getKey").and(takesArguments(0)), getClass().getName() + "$GetKeyAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetKeyAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Context.Key<AtomicBoolean> onExit() {
      return GrpcServerStreamTracerState.getKey();
    }
  }
}
