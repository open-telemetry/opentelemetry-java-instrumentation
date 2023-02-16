/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.getSnippetInjectionHelper;
import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Injection.getInjectionState;

import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.InjectionState;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import net.bytebuddy.asm.Advice;

public class Servlet3OutputStreamWriteBytesAndOffsetAdvice {
  @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, suppress = Throwable.class)
  public static boolean methodEnter(
      @Advice.This ServletOutputStream servletOutputStream,
      @Advice.Argument(value = 0) byte[] write,
      @Advice.Argument(value = 1) int off,
      @Advice.Argument(value = 2) int len)
      throws IOException {
    InjectionState state = getInjectionState(servletOutputStream);
    if (state == null) {
      return true;
    }
    // if handleWrite return true, then it means the injection has happened and the 'write'
    // manipulate is done. the function would return false then, meaning skip the original write
    // function
    return !getSnippetInjectionHelper().handleWrite(write, off, len, state, servletOutputStream);
  }
}
