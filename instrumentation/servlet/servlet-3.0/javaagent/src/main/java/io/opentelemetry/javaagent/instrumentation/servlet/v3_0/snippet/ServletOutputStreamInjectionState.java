/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateStore.Reference;
import javax.annotation.Nullable;
import javax.servlet.ServletOutputStream;

public class ServletOutputStreamInjectionState {
  private static final VirtualField<ServletOutputStream, Reference> INJECTION_STATE =
      VirtualField.find(ServletOutputStream.class, Reference.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, Servlet3SnippetInjectingResponseWrapper wrapper) {
    InjectionState state = getInjectionState(servletOutputStream);
    if (state == null || state.getWrapper() != wrapper) {
      state = new InjectionState(wrapper);
      wrapper.retainInjectionState(state);
      INJECTION_STATE.set(servletOutputStream, new Reference(state));
    }
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    Reference reference = INJECTION_STATE.get(servletOutputStream);
    return reference == null ? null : reference.get();
  }

  private ServletOutputStreamInjectionState() {}
}
