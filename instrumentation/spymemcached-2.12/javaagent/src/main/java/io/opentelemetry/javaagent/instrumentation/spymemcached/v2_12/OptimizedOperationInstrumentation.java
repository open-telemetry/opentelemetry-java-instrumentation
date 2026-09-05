/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.ops.Operation;

class OptimizedOperationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "net.spy.memcached.protocol.ascii.OptimizedGetImpl",
        "net.spy.memcached.protocol.binary.OptimizedGetImpl",
        "net.spy.memcached.protocol.binary.OptimizedSetImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("addOperation")
            .and(takesArguments(1))
            .and(takesArgument(0, hasSuperType(named("net.spy.memcached.ops.Operation")))),
        getClass().getName() + "$AddOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddOperationAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Operation optimizedOperation, @Advice.Argument(0) Operation operation) {
      SpymemcachedRequestHolder.propagateOperation(optimizedOperation, operation);
    }
  }
}
