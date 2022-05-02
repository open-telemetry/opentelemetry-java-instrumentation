/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v10_0.Tomcat10Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

@SuppressWarnings("unused")
public class Tomcat10ServerHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) Response response,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    Context parentContext = Java8BytecodeBridge.currentContext();
    if (!helper().shouldStart(parentContext, request)) {
      return;
    }

    context = helper().start(parentContext, request);

    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    helper().end(request, response, throwable, context, scope);
  }
}
