/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.ops.Operation;

class MemcachedConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("net.spy.memcached.MemcachedConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("addOperation")
            .and(
                takesArguments(
                    "net.spy.memcached.MemcachedNode", "net.spy.memcached.ops.Operation")),
        getClass().getName() + "$AddOperationAdvice");
    transformer.applyAdviceToMethod(
        named("redistributeOperation").and(takesArguments("net.spy.memcached.ops.Operation")),
        getClass().getName() + "$RedistributeOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddOperationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(1) Operation operation) {
      SpymemcachedRequestContext.trackOperation(Context.current(), operation);
    }
  }

  @SuppressWarnings("unused")
  public static class RedistributeOperationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter(@Advice.Argument(0) Operation operation) {
      return SpymemcachedRequestContext.startRetry(operation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
