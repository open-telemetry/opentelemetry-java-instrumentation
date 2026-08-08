/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ktor.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.ktor.client.call.HttpClientCall;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.ktor.common.v2_0.internal.KtorClientTelemetryUtil;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Bridges the library extension {@code HttpClientCall.getOpenTelemetryContext()} so it works under
 * the agent. The library stores an agent-shaded {@code Context} on the call; the application-side
 * method body reads it as an application {@code Context}, which fails with a {@code
 * ClassCastException}. This advice reads the shaded context via the agent-shaded util, converts it
 * to an application context, and skips the original body.
 */
class KtorClientExtensionsInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // the "application." prefix targets the application's (unshaded) class; without it the agent
    // remaps the matcher to the shaded helper copy, which is never loaded as an application class
    return named("application.io.opentelemetry.instrumentation.ktor.v3_0.KtorClientExtensionsKt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getOpenTelemetryContext")
            .and(takesArgument(0, named("io.ktor.client.call.HttpClientCall"))),
        getClass().getName() + "$GetOpenTelemetryContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetOpenTelemetryContextAdvice {

    @Nullable
    @Advice.OnMethodEnter(
        skipOn = Advice.OnNonDefaultValue.class,
        suppress = Throwable.class,
        inline = false)
    public static application.io.opentelemetry.context.Context enter(
        @Advice.Argument(0) HttpClientCall call) {
      Context agentContext = KtorClientTelemetryUtil.getOpenTelemetryContext(call);
      if (agentContext == null) {
        return null;
      }
      return AgentContextStorage.toApplicationContext(agentContext);
    }

    @Nullable
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static application.io.opentelemetry.context.Context onExit(
        @Advice.Return application.io.opentelemetry.context.Context originalResult,
        @Advice.Enter @Nullable application.io.opentelemetry.context.Context context) {
      return context != null ? context : originalResult;
    }
  }
}
