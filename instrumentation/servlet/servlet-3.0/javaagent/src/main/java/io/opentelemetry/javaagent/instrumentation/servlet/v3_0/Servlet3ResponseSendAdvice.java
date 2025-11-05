/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3ResponseSendAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Servlet3ResponseAdviceScope start(
      @Advice.Origin("#t") Class<?> declaringClass, @Advice.Origin("#m") String methodName) {
    return new Servlet3ResponseAdviceScope(
        CallDepth.forClass(HttpServletResponse.class), declaringClass, methodName);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable, @Advice.Enter Servlet3ResponseAdviceScope adviceScope) {
    adviceScope.exit(throwable);
  }
}
