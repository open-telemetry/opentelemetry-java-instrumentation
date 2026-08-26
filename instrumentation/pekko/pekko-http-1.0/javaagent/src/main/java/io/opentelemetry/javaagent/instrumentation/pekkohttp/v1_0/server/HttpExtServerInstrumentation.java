/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.settings.ServerSettings;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.scaladsl.Flow;
import scala.Function1;
import scala.concurrent.Future;

class HttpExtServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("bindAndHandle")
            .and(takesArgument(0, named("org.apache.pekko.stream.scaladsl.Flow"))),
        getClass().getName() + "$PekkoBindAndHandleAdvice");

    transformer.applyAdviceToMethod(
        named("bindAndHandleAsync")
            .and(takesArgument(0, named("scala.Function1")))
            .and(takesArgument(4, named("org.apache.pekko.http.scaladsl.settings.ServerSettings")))
            .and(takesArgument(7, named("org.apache.pekko.stream.Materializer"))),
        getClass().getName() + "$PekkoBindAndHandleAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class PekkoBindAndHandleAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Flow<HttpRequest, HttpResponse, ?> wrapHandler(
        @Advice.Argument(value = 0) Flow<HttpRequest, HttpResponse, ?> handler) {
      return PekkoFlowWrapper.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class PekkoBindAndHandleAsyncAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Function1<HttpRequest, Future<HttpResponse>> wrapHandler(
        @Advice.Argument(0) Function1<HttpRequest, Future<HttpResponse>> handler,
        @Advice.Argument(4) ServerSettings settings,
        @Advice.Argument(7) Materializer materializer) {

      if (!settings.previewServerSettings().enableHttp2()) {
        return handler;
      }

      return PekkoAsyncHandlerWrapper.wrap(handler, materializer.executionContext());
    }
  }
}
