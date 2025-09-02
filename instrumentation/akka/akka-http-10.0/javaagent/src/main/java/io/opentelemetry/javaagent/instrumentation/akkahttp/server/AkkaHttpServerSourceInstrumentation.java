/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AkkaHttpServerSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.Http$IncomingConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleWith").and(takesArgument(0, named("akka.stream.scaladsl.Flow"))),
        this.getClass().getName() + "$AkkaBindAndHandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class AkkaBindAndHandleAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Flow<HttpRequest, HttpResponse, ?> wrapHandler(
        @Advice.Argument(0) Flow<HttpRequest, HttpResponse, ?> handler) {
      return AkkaFlowWrapper.wrap(handler);
    }
  }
}
