/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.TomcatContentLength;
import jakarta.servlet.ServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet5TomcatResponseContentLengthAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
  public static void onExit(
      @Advice.This ServletResponse response,
      @Advice.FieldValue("response") Object bodyResponse,
      @Advice.Origin("#m") String methodName,
      @Advice.AllArguments Object[] arguments) {
    if (!isContentLengthSetter(methodName, arguments)) {
      return;
    }
    InjectionState state = Servlet5ResponseInjectionState.get(response);
    if (state != null) {
      TomcatContentLength.record(bodyResponse, state);
    }
  }

  private static boolean isContentLengthSetter(String methodName, Object[] arguments) {
    return methodName.startsWith("setContentLength")
        || (arguments[0] instanceof String
            && "Content-Length".equalsIgnoreCase((String) arguments[0]));
  }
}
