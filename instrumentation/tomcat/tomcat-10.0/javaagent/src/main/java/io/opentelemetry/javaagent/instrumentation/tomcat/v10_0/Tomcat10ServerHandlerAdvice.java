/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v10_0.Tomcat10Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

@SuppressWarnings("unused")
public class Tomcat10ServerHandlerAdvice {

  public static class AdviceScope {
    private final Context context;
    private final Scope scope;

    private AdviceScope(Context context, Scope scope) {
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(Request request, Response response) {
      Context parentContext = Context.current();
      if (!helper().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = helper().start(parentContext, request);

      Scope scope = context.makeCurrent();

      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, response, Tomcat10ResponseMutator.INSTANCE);

      return new AdviceScope(context, scope);
    }

    private void end(Request request, Response response, Throwable throwable) {
      helper().end(request, response, throwable, context, scope);
    }
  }

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AdviceScope onEnter(
      @Advice.Argument(0) Request request, @Advice.Argument(1) Response response) {
    return AdviceScope.start(request, response);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) Response response,
      @Advice.Thrown @Nullable Throwable throwable,
      @Advice.Enter AdviceScope adviceScope) {
    if (adviceScope != null) {
      adviceScope.end(request, response, throwable);
    }
  }
}
