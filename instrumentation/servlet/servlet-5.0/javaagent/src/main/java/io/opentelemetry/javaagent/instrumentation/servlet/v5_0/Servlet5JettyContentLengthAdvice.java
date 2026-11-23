/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.JettyContentLength;
import jakarta.servlet.ServletResponse;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet5JettyContentLengthAdvice {
  private static final Logger logger =
      Logger.getLogger(Servlet5JettyContentLengthAdvice.class.getName());

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
  public static void onExit(
      @Advice.This Object response,
      @Advice.Origin("#m") String methodName,
      @Advice.AllArguments Object[] arguments,
      @Advice.Thrown @Nullable Throwable throwable) {
    if (throwable != null || !(response instanceof ServletResponse)) {
      return;
    }
    try {
      InjectionState state = Servlet5ResponseInjectionState.get((ServletResponse) response);
      if (state != null) {
        JettyContentLength.recordAcceptedContentLength(response, state, methodName, arguments);
      }
    } catch (Throwable t) {
      logger.log(FINE, "Failed to track Jetty's Content-Length update", t);
    }
  }

  private Servlet5JettyContentLengthAdvice() {}
}
