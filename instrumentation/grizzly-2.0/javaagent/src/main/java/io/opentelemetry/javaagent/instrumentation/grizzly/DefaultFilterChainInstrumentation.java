/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlySingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;

public class DefaultFilterChainInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.filterchain.DefaultFilterChain");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPrivate())
            .and(named("notifyFailure"))
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(takesArgument(1, Throwable.class)),
        DefaultFilterChainInstrumentation.class.getName() + "$NotifyFailureAdvice");
  }

  @SuppressWarnings("unused")
  public static class NotifyFailureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onFail(
        @Advice.Argument(0) FilterChainContext ctx, @Advice.Argument(1) Throwable throwable) {
      Context context = GrizzlyStateStorage.removeContext(ctx);
      HttpRequestPacket request = GrizzlyStateStorage.removeRequest(ctx);
      if (context != null && request != null) {
        Throwable error = GrizzlyExceptionHolder.getOrDefault(context, throwable);
        instrumenter().end(context, request, null, error);
      }
    }
  }
}
