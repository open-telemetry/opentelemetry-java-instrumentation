/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet.Servlet5SnippetInjectingResponseWrapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet5RequestDispatcherForwardAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
  public static void onEnter(@Advice.Argument(0) ServletRequest request) {
    if (request instanceof HttpServletRequest) {
      Servlet5SnippetInjectingResponseWrapper.beginForward((HttpServletRequest) request);
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
  public static void onExit(
      @Advice.Argument(0) ServletRequest request, @Advice.Thrown @Nullable Throwable throwable) {
    if (request instanceof HttpServletRequest) {
      Servlet5SnippetInjectingResponseWrapper.finishForward(
          (HttpServletRequest) request, throwable == null);
    }
  }
}
