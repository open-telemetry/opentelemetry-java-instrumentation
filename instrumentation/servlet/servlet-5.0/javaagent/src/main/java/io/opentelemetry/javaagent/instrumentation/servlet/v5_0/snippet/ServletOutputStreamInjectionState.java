/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateReference;
import jakarta.servlet.ServletOutputStream;
import javax.annotation.Nullable;

public class ServletOutputStreamInjectionState {
  private static final VirtualField<ServletOutputStream, InjectionStateReference> VIRTUAL_FIELD =
      VirtualField.find(ServletOutputStream.class, InjectionStateReference.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, Servlet5SnippetInjectingResponseWrapper wrapper) {
    InjectionState state = wrapper.getStreamInjectionState();
    InjectionStateReference reference = VIRTUAL_FIELD.get(servletOutputStream);
    if (reference != null && reference.get() == state) {
      return;
    }
    VIRTUAL_FIELD.set(servletOutputStream, new InjectionStateReference(state));
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    InjectionStateReference reference = VIRTUAL_FIELD.get(servletOutputStream);
    if (reference == null) {
      return null;
    }
    InjectionState state = reference.get();
    if (state == null || !state.isCurrentRequest()) {
      VIRTUAL_FIELD.set(servletOutputStream, null);
      return null;
    }
    return state;
  }

  private ServletOutputStreamInjectionState() {}
}
