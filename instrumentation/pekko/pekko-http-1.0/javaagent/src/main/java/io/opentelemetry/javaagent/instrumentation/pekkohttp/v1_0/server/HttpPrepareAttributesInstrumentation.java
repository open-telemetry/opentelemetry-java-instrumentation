/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.scaladsl.Tcp.IncomingConnection;

class HttpPrepareAttributesInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.scaladsl.Http$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("prepareAttributes")
            .and(takesArgument(1, named("org.apache.pekko.stream.scaladsl.Tcp$IncomingConnection")))
            .and(returns(named("org.apache.pekko.stream.Attributes"))),
        getClass().getName() + "$PrepareAttributesAdvice");
  }

  @SuppressWarnings("unused")
  public static class PrepareAttributesAdvice {

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Attributes onExit(
        @Advice.Argument(1) IncomingConnection connection, @Advice.Return Attributes attributes) {
      return attributes.and(new PekkoHttpServerRemoteAddress(connection.remoteAddress()));
    }
  }
}
