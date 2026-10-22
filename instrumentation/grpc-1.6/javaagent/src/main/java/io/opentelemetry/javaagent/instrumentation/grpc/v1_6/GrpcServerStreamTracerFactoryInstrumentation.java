/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.grpc.ServerStreamTracer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GrpcServerStreamTracerFactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.grpc.internal.AbstractServerImplBuilder", "io.grpc.internal.ServerImplBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getTracerFactories").and(takesArguments(0)),
        getClass().getName() + "$GetTracerFactoriesAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetTracerFactoriesAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static List<? extends ServerStreamTracer.Factory> onExit(
        @Advice.Return List<? extends ServerStreamTracer.Factory> factories) {
      return GrpcSingletons.deduplicateServerStreamTracerFactories(factories);
    }
  }
}
