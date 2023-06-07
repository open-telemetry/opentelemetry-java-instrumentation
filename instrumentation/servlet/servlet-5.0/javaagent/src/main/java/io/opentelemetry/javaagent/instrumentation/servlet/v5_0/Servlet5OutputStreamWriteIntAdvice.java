/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.getSnippetInjectionHelper;

import io.opentelemetry.javaagent.instrumentation.servlet.snippet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet.ServletOutputStreamInjectionState;
import jakarta.servlet.ServletOutputStream;
import java.io.IOException;
import net.bytebuddy.asm.Advice;

public class Servlet5OutputStreamWriteIntAdvice {

  @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, suppress = Throwable.class)
  public static boolean methodEnter(
      @Advice.This ServletOutputStream servletOutputStream, @Advice.Argument(0) int write)
      throws IOException {
    InjectionState state = ServletOutputStreamInjectionState.getInjectionState(servletOutputStream);
    if (state == null) {
      return true;
    }
    // if handleWrite returns true, then it means the original bytes + the snippet were written
    // to the servletOutputStream, and so we no longer need to execute the original method
    // call (see skipOn above)
    // if it returns false, then it means nothing was written to the servletOutputStream and the
    // original method call should be executed
    return !getSnippetInjectionHelper().handleWrite(state, servletOutputStream, write);
  }
}
