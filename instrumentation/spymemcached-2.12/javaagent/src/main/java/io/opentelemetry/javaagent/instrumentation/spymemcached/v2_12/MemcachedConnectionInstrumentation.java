/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
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
            .and(takesArguments(2))
            .and(takesArgument(0, named("net.spy.memcached.MemcachedNode")))
            .and(takesArgument(1, named("net.spy.memcached.ops.Operation"))),
        getClass().getName() + "$AddOperationAdvice");
    transformer.applyAdviceToMethod(
        named("redistributeOperation")
            .and(takesArguments(1))
            .and(takesArgument(0, named("net.spy.memcached.ops.Operation"))),
        getClass().getName() + "$RedistributeOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddOperationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(1) Operation operation) {
      SpymemcachedRequestHolder.associateOperation(Java8BytecodeBridge.currentContext(), operation);
    }
  }

  @SuppressWarnings("unused")
  public static class RedistributeOperationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static SpymemcachedRequestHolder.RetryScope onEnter(
        @Advice.Argument(0) Operation operation) {
      return SpymemcachedRequestHolder.startRetry(operation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable SpymemcachedRequestHolder.RetryScope retryScope) {
      if (retryScope != null) {
        retryScope.close();
      }
    }
  }
}
