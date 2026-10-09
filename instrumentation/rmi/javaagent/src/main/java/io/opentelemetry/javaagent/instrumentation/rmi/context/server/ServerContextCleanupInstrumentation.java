/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.rmi.ThreadLocalContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServerContextCleanupInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "sun.rmi.server.UnicastServerRef", "sun.rmi.transport.tcp.TCPTransport$ConnectionHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("run").and(takesArguments(0)).or(named("dispatch").and(takesArguments(2))),
        getClass().getName() + "$CleanupAdvice");
  }

  @SuppressWarnings("unused")
  public static class CleanupAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      // Unmarshalling can fail before the remote method consumes the payload, or the connection can
      // close before dispatch starts. Neither path should leave context on a reusable worker.
      ThreadLocalContext.INSTANCE.set(null);
    }
  }
}
