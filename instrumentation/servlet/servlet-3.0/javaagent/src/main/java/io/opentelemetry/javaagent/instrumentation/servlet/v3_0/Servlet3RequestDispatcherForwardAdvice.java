/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Servlet3SnippetInjectingResponseWrapper;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3RequestDispatcherForwardAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
  public static void onEnter(@Advice.Argument(0) ServletRequest request) {
    if (request instanceof HttpServletRequest) {
      Servlet3SnippetInjectingResponseWrapper.resetForForward((HttpServletRequest) request);
    }
  }
}
