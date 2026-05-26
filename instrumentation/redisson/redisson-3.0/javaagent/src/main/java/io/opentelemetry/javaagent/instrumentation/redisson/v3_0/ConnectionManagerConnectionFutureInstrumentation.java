/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.ContextPropagatingCompletableFuture;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.api.RFuture;

class ConnectionManagerConnectionFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.redisson.connection.ConnectionManager"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.redisson.connection.ConnectionManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("connectionReadOp", "connectionWriteOp").and(takesArguments(2)),
        getClass().getName() + "$WrapConnectionFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapConnectionFutureAdvice {

    @AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC)
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onExit(
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) @Nullable Object future) {
      Context context = currentContext();
      if (future instanceof RFuture) {
        return ContextPropagatingRFuture.wrap((RFuture<?>) future, context);
      }
      if (future instanceof CompletableFuture) {
        return ContextPropagatingCompletableFuture.wrap((CompletableFuture<?>) future, context);
      }
      return future;
    }
  }
}
