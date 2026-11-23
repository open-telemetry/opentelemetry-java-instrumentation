/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.ops.OperationStatus;

class MemcachedFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "net.spy.memcached.internal.OperationFuture",
        "net.spy.memcached.internal.GetFuture",
        "net.spy.memcached.internal.BulkGetFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("set").and(takesArgument(1, named("net.spy.memcached.ops.OperationStatus"))),
        getClass().getName() + "$SetStatusAdvice");
    transformer.applyAdviceToMethod(
        named("setStatus").and(takesArgument(0, named("net.spy.memcached.ops.OperationStatus"))),
        getClass().getName() + "$SetBulkStatusAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetStatusAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object future, @Advice.Argument(1) OperationStatus status) {
      SpymemcachedSingletons.captureResponseStatus(future, status);
    }
  }

  @SuppressWarnings("unused")
  public static class SetBulkStatusAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object future, @Advice.Argument(0) OperationStatus status) {
      SpymemcachedSingletons.captureResponseStatus(future, status);
    }
  }
}
