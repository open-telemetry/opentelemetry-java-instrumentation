/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateStore;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateStore.Reference;
import jakarta.servlet.ServletOutputStream;
import javax.annotation.Nullable;

public class ServletOutputStreamInjectionState {
  private static final VirtualField<ServletOutputStream, Reference> VIRTUAL_FIELD =
      VirtualField.find(ServletOutputStream.class, Reference.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, Servlet5SnippetInjectingResponseWrapper wrapper) {
    InjectionStateStore store = wrapper.injectionStateStore();
    synchronized (store) {
      InjectionState previous = getInjectionState(servletOutputStream);
      if (previous == null || previous.getWrapper() != wrapper) {
        InjectionState state = new InjectionState(wrapper);
        store.replace(previous, state);
        VIRTUAL_FIELD.set(servletOutputStream, new Reference(state));
      }
    }
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    Reference reference = VIRTUAL_FIELD.get(servletOutputStream);
    return reference == null ? null : reference.get();
  }

  private ServletOutputStreamInjectionState() {}
}
