/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Attributes;
import akka.stream.scaladsl.Flow;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class HttpExtServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("bindAndHandle").and(takesArgument(0, named("akka.stream.scaladsl.Flow"))),
        getClass().getName() + "$AkkaBindAndHandleAdvice");
    transformer.applyAdviceToMethod(
        nameEndsWith("prepareAttributes")
            .and(takesArguments(2))
            .and(takesArgument(1, InetSocketAddress.class))
            .and(returns(named("akka.stream.Attributes"))),
        getClass().getName() + "$PrepareAttributesAdvice");
  }

  @SuppressWarnings("unused")
  public static class AkkaBindAndHandleAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Flow<HttpRequest, HttpResponse, ?> wrapHandler(
        @Advice.Argument(0) Flow<HttpRequest, HttpResponse, ?> handler) {
      return AkkaFlowWrapper.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class PrepareAttributesAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Attributes onExit(
        @Advice.Argument(1) InetSocketAddress remoteAddress, @Advice.Return Attributes attributes) {
      return AkkaFlowWrapper.withRemoteAddress(attributes, remoteAddress);
    }
  }
}
