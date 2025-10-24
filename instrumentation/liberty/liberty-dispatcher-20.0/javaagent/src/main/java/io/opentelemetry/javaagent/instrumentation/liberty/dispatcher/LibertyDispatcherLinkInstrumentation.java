/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import static io.opentelemetry.javaagent.instrumentation.liberty.dispatcher.LibertyDispatcherSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.ibm.ws.http.channel.internal.inbound.HttpInboundServiceContextImpl;
import com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink;
import com.ibm.wsspi.http.channel.values.StatusCodes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumenting
 * https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
 * We instrument sendResponse method that is called when no application has been deployed under
 * requested context root or something goes horribly wrong and server responds with Internal Server
 * Error
 */
public class LibertyDispatcherLinkInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
    transformer.applyAdviceToMethod(
        named("sendResponse")
            .and(takesArgument(0, named("com.ibm.wsspi.http.channel.values.StatusCodes")))
            .and(takesArgument(1, named(String.class.getName())))
            .and(takesArgument(2, named(Exception.class.getName())))
            .and(takesArgument(3, named(boolean.class.getName()))),
        this.getClass().getName() + "$SendResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendResponseAdvice {

    public static class AdviceLocals {
      public LibertyRequest request;
      public Context context;
      public Scope scope;
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceLocals onEnter(
        @Advice.FieldValue("isc") HttpInboundServiceContextImpl isc) {

      AdviceLocals locals = new AdviceLocals();
      Context parentContext = Java8BytecodeBridge.currentContext();
      locals.request =
          new LibertyRequest(
              isc.getRequest(),
              isc.getLocalAddr(),
              isc.getLocalPort(),
              isc.getRemoteAddr(),
              isc.getRemotePort());
      if (!instrumenter().shouldStart(parentContext, locals.request)) {
        return locals;
      }
      locals.context = instrumenter().start(parentContext, locals.request);
      locals.scope = locals.context.makeCurrent();
      return locals;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This HttpDispatcherLink httpDispatcherLink,
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(value = 0) StatusCodes statusCode,
        @Advice.Argument(value = 2) Exception failure,
        @Advice.Enter AdviceLocals locals) {

      if (locals.scope == null) {
        return;
      }
      locals.scope.close();

      LibertyResponse response = new LibertyResponse(httpDispatcherLink, statusCode);

      Throwable t = failure != null ? failure : throwable;
      instrumenter().end(locals.context, locals.request, response, t);
    }
  }
}
