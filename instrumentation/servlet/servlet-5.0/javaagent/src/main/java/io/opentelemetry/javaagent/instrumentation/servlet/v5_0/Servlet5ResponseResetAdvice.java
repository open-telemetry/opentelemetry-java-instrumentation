/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import jakarta.servlet.ServletResponse;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet5ResponseResetAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
  public static void onExit(
      @Advice.This ServletResponse response,
      @Advice.Origin("#m") String methodName,
      @Advice.Thrown @Nullable Throwable throwable) {
    if (throwable != null) {
      return;
    }
    InjectionState state = Servlet5ResponseInjectionState.get(response);
    if (state == null) {
      return;
    }
    if ("resetBuffer".equals(methodName)) {
      state.resetBuffer();
    } else {
      state.reset();
    }
  }
}
