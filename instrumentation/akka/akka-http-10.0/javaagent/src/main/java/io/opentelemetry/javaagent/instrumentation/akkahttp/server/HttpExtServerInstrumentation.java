/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpExtServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("bindAndHandle").and(takesArgument(0, named("akka.stream.scaladsl.Flow"))),
        this.getClass().getName() + "$AkkaHttpFlowAdvice");
  }

  @SuppressWarnings("unused")
  public static class AkkaHttpFlowAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
        akka.stream.scaladsl.Flow<HttpRequest, HttpResponse, ?> handler) {
      handler = handler.join(new AkkaHttpServerInstrumentationModule.FlowWrapper()).withAttributes(AkkaHttpServerSingletons.OTEL_DISPATCHER_ATTRIBUTES);
    }
  }
}
