/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.getSnippetInjectionHelper;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Servlet3SnippetInjectingResponseWrapper;
import javax.annotation.Nullable;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class Servlet3Advice {

  @AssignReturned.ToArguments({
    @ToArgument(value = 0, index = 1),
    @ToArgument(value = 1, index = 2)
  })
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Object[] onEnter(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) Object servletOrFilter,
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse originalResponse) {

    ServletResponse response = originalResponse;

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return new Object[] {null, request, response};
    }

    String snippet = getSnippetInjectionHelper().getSnippet();
    if (!snippet.isEmpty()
        && !((HttpServletResponse) response)
            .containsHeader(Servlet3SnippetInjectingResponseWrapper.FAKE_SNIPPET_HEADER)) {
      response =
          new Servlet3SnippetInjectingResponseWrapper((HttpServletResponse) response, snippet);
    }
    Servlet3RequestAdviceScope adviceScope =
        new Servlet3RequestAdviceScope(
            CallDepth.forClass(AppServerBridge.getCallDepthKey()),
            (HttpServletRequest) request,
            (HttpServletResponse) response,
            servletOrFilter);
    return new Object[] {adviceScope, request, response};
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown @Nullable Throwable throwable,
      @Advice.Enter Object[] enterResult) {
    Servlet3RequestAdviceScope adviceScope = (Servlet3RequestAdviceScope) enterResult[0];
    if (adviceScope == null
        || !(request instanceof HttpServletRequest)
        || !(response instanceof HttpServletResponse)) {
      return;
    }
    adviceScope.exit(throwable, (HttpServletRequest) request, (HttpServletResponse) response);
  }
}
