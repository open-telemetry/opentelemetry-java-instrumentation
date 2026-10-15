/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import akka.stream.Attributes;
import akka.stream.scaladsl.Tcp.IncomingConnection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class HttpServerAttributesInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.Http$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("prepareAttributes")
            .and(takesArguments(2))
            .and(takesArgument(1, named("akka.stream.scaladsl.Tcp$IncomingConnection")))
            .and(returns(named("akka.stream.Attributes"))),
        getClass().getName() + "$PrepareAttributesAdvice");
  }

  @SuppressWarnings("unused")
  public static class PrepareAttributesAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Attributes onExit(
        @Advice.Argument(1) IncomingConnection connection, @Advice.Return Attributes attributes) {
      return AkkaFlowWrapper.withRemoteAddress(attributes, connection.remoteAddress());
    }
  }
}
