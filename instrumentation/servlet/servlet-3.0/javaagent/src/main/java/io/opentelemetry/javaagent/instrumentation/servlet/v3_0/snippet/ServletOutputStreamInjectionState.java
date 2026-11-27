/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import javax.servlet.ServletOutputStream;

public class ServletOutputStreamInjectionState {
  // The fallback map has weak stream keys and strong values, so a strong InjectionState could
  // reach its stream through the response wrapper and keep the key alive. The request attribute
  // strongly owns the state for the request lifetime, including async writes.
  private static final VirtualField<ServletOutputStream, WeakReference<InjectionState>>
      INJECTION_STATE = VirtualField.find(ServletOutputStream.class, WeakReference.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, InjectionState state) {
    WeakReference<InjectionState> reference = INJECTION_STATE.get(servletOutputStream);
    if (reference != null && reference.get() == state) {
      return;
    }
    INJECTION_STATE.set(servletOutputStream, new WeakReference<>(state));
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    WeakReference<InjectionState> reference = INJECTION_STATE.get(servletOutputStream);
    if (reference == null) {
      return null;
    }
    InjectionState state = reference.get();
    if (state == null || (!state.isHeadTagWritten() && !state.isCurrentRequest())) {
      INJECTION_STATE.set(servletOutputStream, null);
      return null;
    }
    return state;
  }

  private ServletOutputStreamInjectionState() {}
}
