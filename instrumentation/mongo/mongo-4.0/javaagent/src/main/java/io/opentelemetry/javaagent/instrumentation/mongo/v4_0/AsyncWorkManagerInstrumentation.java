/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AsyncWorkManagerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.internal.connection.DefaultConnectionPool$AsyncWorkManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // this method sets up a new thread pool and submits a task to it, we need to avoid context
    // propagating there
    transformer.applyAdviceToMethod(
        named("initUnlessClosed"),
        AsyncWorkManagerInstrumentation.class.getName() + "$DisablePropagationAdvice");
  }

  @SuppressWarnings("unused")
  public static class DisablePropagationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      if (Java8BytecodeBridge.currentContext() != Java8BytecodeBridge.rootContext()) {
        // Prevent context from leaking by running this method under root context.
        // Root context is not propagated by executor instrumentation.
        return Java8BytecodeBridge.rootContext().makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
