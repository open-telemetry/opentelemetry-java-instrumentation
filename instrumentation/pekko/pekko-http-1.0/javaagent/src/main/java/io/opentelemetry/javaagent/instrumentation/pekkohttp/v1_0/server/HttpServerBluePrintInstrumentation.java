/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.stream.scaladsl.BidiFlow;

public class HttpServerBluePrintInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.impl.engine.server.HttpServerBluePrint$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("requestPreparation")
            .and(returns(named("org.apache.pekko.stream.scaladsl.BidiFlow"))),
        this.getClass().getName() + "$PekkoBindAndHandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class PekkoBindAndHandleAdvice {

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static BidiFlow<HttpResponse, ?, ?, HttpRequest, ?> wrapHandler(
        @Advice.Return BidiFlow<HttpResponse, ?, ?, HttpRequest, ?> handler) {

      return PekkoHttpServerTracer.wrap(handler);
    }
  }
}
