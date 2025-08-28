/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.nats.client.Dispatcher"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("start")),
        DispatcherInstrumentation.class.getName() + "$DisablePropagationAdvice");
  }

  @SuppressWarnings("unused")
  public static class DisablePropagationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      // NatsConnection creates a long-running dispatcher on the first `request` call
      // leading to a leaked context over the `publish` span creating the dispatcher.
      // Dispatchers are usually background long-lived threads, we can force root,
      // as we're not expecting to be anything else than entry points for network messages.
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
