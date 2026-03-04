/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sttp.tapir.server.interceptor.Interceptor;
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions;

public class TapirPathInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("pekkoHttpServerOptions").and(takesArguments(0)),
        this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings({"unused", "unchecked"}) // options.prependInterceptor takes higher-kinded type, not possible from java
  public static class ApplyAdvice {
    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Object onExit(
        @Advice.Return PekkoHttpServerOptions options) {

      return options.prependInterceptor((Interceptor) TapirRouteHandler.interceptor());
    }
  }
}
