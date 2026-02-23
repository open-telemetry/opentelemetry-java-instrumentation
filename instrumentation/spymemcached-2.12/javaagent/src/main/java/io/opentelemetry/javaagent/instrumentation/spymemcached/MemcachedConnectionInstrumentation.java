/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
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
            .and(takesArgument(0, named("net.spy.memcached.MemcachedNode")))
            .and(takesArgument(1, named("net.spy.memcached.ops.Operation"))),
        this.getClass().getName() + "$AddOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddOperationAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Argument(1) Operation operation) {
      // we are reading node from operation instead of using the node that was passed to the method,
      // because we want to get the node that is actually handling the request, which could be
      // different from the one passed to the method in case of retries
      SpymemcachedRequestHolder.setHandlingNode(
          Java8BytecodeBridge.currentContext(), operation.getHandlingNode());
    }
  }
}
