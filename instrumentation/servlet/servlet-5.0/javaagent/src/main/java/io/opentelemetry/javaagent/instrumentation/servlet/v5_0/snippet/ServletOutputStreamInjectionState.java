/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import jakarta.servlet.ServletOutputStream;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;

public class ServletOutputStreamInjectionState {
  private static final VirtualField<ServletOutputStream, WeakReference<InjectionState>>
      VIRTUAL_FIELD = VirtualField.find(ServletOutputStream.class, WeakReference.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, InjectionState state) {
    WeakReference<InjectionState> reference = VIRTUAL_FIELD.get(servletOutputStream);
    if (reference != null && reference.get() == state) {
      return;
    }
    VIRTUAL_FIELD.set(servletOutputStream, new WeakReference<>(state));
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    WeakReference<InjectionState> reference = VIRTUAL_FIELD.get(servletOutputStream);
    if (reference == null) {
      return null;
    }
    InjectionState state = reference.get();
    if (state == null || (!state.isHeadTagWritten() && !state.isCurrentRequest())) {
      VIRTUAL_FIELD.set(servletOutputStream, null);
      return null;
    }
    return state;
  }

  private ServletOutputStreamInjectionState() {}
}
