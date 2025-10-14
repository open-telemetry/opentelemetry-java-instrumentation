/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.grpc.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GrpcContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.grpc.Context");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("storage"))
            .and(returns(named("io.grpc.Context$Storage"))),
        GrpcContextInstrumentation.class.getName() + "$ContextBridgeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ContextBridgeAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Context.Storage onEnter() {
      return GrpcSingletons.getStorage();
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static Context.Storage onExit(
        @Advice.Return Context.Storage originalStorage, @Advice.Enter Context.Storage ourStorage) {
      return ourStorage != null ? ourStorage : GrpcSingletons.setStorage(originalStorage);
    }
  }
}
