/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * A request that fails to parse, for example because of an illegal request-target, is answered by
 * {@code HttpServerBluePrint$ControllerStage}, which sits below the stage that the server
 * instrumentation wraps. The rejected request never becomes an {@code HttpRequest} and never
 * reaches the user handler, so the only hook that sees it is the parsing error handler.
 */
class ParsingErrorHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.pekko.http.ParsingErrorHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.pekko.http.ParsingErrorHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // a scala implementation returns the more specific scaladsl HttpResponse and gets a bridge
    // method for the signature declared on ParsingErrorHandler; skipping the bridge leaves exactly
    // one match per implementation, whichever of the two return types it declares
    transformer.applyAdviceToMethod(
        named("handle").and(not(isAbstract())).and(not(isBridge())).and(takesArguments(4)),
        getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return Object response) {
      PekkoHttpParsingErrorSingletons.emitSpan((HttpResponse) response);
    }
  }
}
