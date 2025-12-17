/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.handlingNodeThreadLocal;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.Operation;

public class MemcachedConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("net.spy.memcached.MemcachedConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("addOperation"))
            .and(takesArguments(2))
            .and(takesArguments(MemcachedNode.class, Operation.class)),
        this.getClass().getName() + "$AddOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) MemcachedNode node) {
      if (node != null) {
        handlingNodeThreadLocal.set(node);
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      handlingNodeThreadLocal.remove();
    }
  }
}
