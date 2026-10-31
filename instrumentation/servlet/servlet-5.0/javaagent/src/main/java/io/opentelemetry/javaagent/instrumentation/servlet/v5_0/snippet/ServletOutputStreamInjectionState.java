/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import jakarta.servlet.ServletOutputStream;
import javax.annotation.Nullable;

public class ServletOutputStreamInjectionState {
  private static final VirtualField<ServletOutputStream, InjectionState> VIRTUAL_FIELD =
      VirtualField.find(ServletOutputStream.class, InjectionState.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, Servlet5SnippetInjectingResponseWrapper wrapper) {
    VIRTUAL_FIELD.set(servletOutputStream, wrapper.getInjectionState());
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    return VIRTUAL_FIELD.get(servletOutputStream);
  }

  private ServletOutputStreamInjectionState() {}
}
