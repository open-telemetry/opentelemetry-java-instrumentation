/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.TomcatContentLength;
import javax.annotation.Nullable;
import javax.servlet.ServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3TomcatResponseContentLengthAdvice {

  public static class HeaderAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ServletResponse response,
        @Advice.FieldValue("response") Object bodyResponse,
        @Advice.Argument(0) String headerName,
        @Advice.Argument(1) @Nullable String headerValue) {
      if ("Content-Length".equalsIgnoreCase(headerName)) {
        recordAccepted(response, bodyResponse, headerValue);
      }
    }
  }

  public static class IntHeaderAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ServletResponse response,
        @Advice.FieldValue("response") Object bodyResponse,
        @Advice.Argument(0) String headerName,
        @Advice.Argument(1) int headerValue) {
      if ("Content-Length".equalsIgnoreCase(headerName)) {
        recordAccepted(response, bodyResponse, headerValue);
      }
    }
  }

  public static class IntContentLengthAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ServletResponse response,
        @Advice.FieldValue("response") Object bodyResponse,
        @Advice.Argument(0) int contentLength) {
      recordAccepted(response, bodyResponse, contentLength);
    }
  }

  public static class LongContentLengthAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ServletResponse response,
        @Advice.FieldValue("response") Object bodyResponse,
        @Advice.Argument(0) long contentLength) {
      recordAccepted(response, bodyResponse, contentLength);
    }
  }

  private static void recordAccepted(
      ServletResponse response, Object bodyResponse, long declaredLength) {
    InjectionState state = Servlet3ResponseInjectionState.get(response);
    if (state != null) {
      TomcatContentLength.recordAccepted(bodyResponse, state, declaredLength);
    }
  }

  private static void recordAccepted(
      ServletResponse response, Object bodyResponse, @Nullable String declaredLength) {
    InjectionState state = Servlet3ResponseInjectionState.get(response);
    if (state != null) {
      TomcatContentLength.recordAccepted(bodyResponse, state, declaredLength);
    }
  }
}
