/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateReference;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import javax.annotation.Nullable;

public class Servlet5ResponseInjectionState {
  private static final VirtualField<ServletResponse, InjectionStateReference> INJECTION_STATE =
      VirtualField.find(ServletResponse.class, InjectionStateReference.class);

  public static void set(ServletResponse response, InjectionState state) {
    InjectionStateReference reference = new InjectionStateReference(state);
    while (response instanceof ServletResponseWrapper) {
      INJECTION_STATE.set(response, reference);
      response = ((ServletResponseWrapper) response).getResponse();
    }
    INJECTION_STATE.set(response, reference);
  }

  @Nullable
  public static InjectionState get(ServletResponse response) {
    InjectionStateReference reference = INJECTION_STATE.get(response);
    if (reference == null) {
      return null;
    }
    InjectionState state = reference.get();
    if (state == null || !state.isCurrentRequest()) {
      INJECTION_STATE.set(response, null);
      return null;
    }
    return state;
  }

  private Servlet5ResponseInjectionState() {}
}
