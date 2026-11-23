/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.TomcatContentLength;
import javax.servlet.ServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3TomcatResponseContentLengthAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
  public static void onExit(
      @Advice.This ServletResponse response, @Advice.FieldValue("response") Object bodyResponse) {
    record(response, bodyResponse);
  }

  public static class HeaderAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ServletResponse response,
        @Advice.FieldValue("response") Object bodyResponse,
        @Advice.Argument(0) String headerName) {
      if ("Content-Length".equalsIgnoreCase(headerName)) {
        record(response, bodyResponse);
      }
    }
  }

  private static void record(ServletResponse response, Object bodyResponse) {
    InjectionState state = Servlet3ResponseInjectionState.get(response);
    if (state != null) {
      TomcatContentLength.record(bodyResponse, state);
    }
  }
}
