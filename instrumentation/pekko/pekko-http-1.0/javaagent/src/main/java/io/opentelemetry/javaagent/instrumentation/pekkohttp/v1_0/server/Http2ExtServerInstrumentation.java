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
import org.apache.pekko.http.impl.engine.http2.Http2Ext;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import scala.Function1;
import scala.concurrent.Future;

/**
 * Instruments the server binding that is used when http/2 is enabled. Such bindings don't go
 * through {@code HttpExt.bindAndHandle}, which only supports http/1.1, so the handler function is
 * wrapped here instead of the handler flow.
 */
class Http2ExtServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.impl.engine.http2.Http2Ext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("bindAndHandleAsync").and(takesArgument(0, named("scala.Function1"))),
        getClass().getName() + "$PekkoBindAndHandleAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class PekkoBindAndHandleAsyncAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Function1<HttpRequest, Future<HttpResponse>> wrapHandler(
        @Advice.Argument(0) Function1<HttpRequest, Future<HttpResponse>> handler,
        @Advice.This Http2Ext thiz) {
      return PekkoHttpServerHandlerWrapper.wrap(handler, thiz.system().dispatcher());
    }
  }
}
