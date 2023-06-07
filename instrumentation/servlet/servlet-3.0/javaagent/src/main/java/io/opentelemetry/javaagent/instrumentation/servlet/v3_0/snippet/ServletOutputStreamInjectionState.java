/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.servlet.snippet.InjectionState;
import javax.annotation.Nullable;
import javax.servlet.ServletOutputStream;

public class ServletOutputStreamInjectionState {
  private static final VirtualField<ServletOutputStream, InjectionState> virtualField =
      VirtualField.find(ServletOutputStream.class, InjectionState.class);

  public static void initializeInjectionStateIfNeeded(
      ServletOutputStream servletOutputStream, Servlet3SnippetInjectingResponseWrapper wrapper) {
    InjectionState state = virtualField.get(servletOutputStream);
    if (!wrapper.isContentTypeTextHtml()) {
      virtualField.set(servletOutputStream, null);
      return;
    }
    if (state == null || state.getWrapper() != wrapper) {
      state = new InjectionState(wrapper);
      virtualField.set(servletOutputStream, state);
    }
  }

  @Nullable
  public static InjectionState getInjectionState(ServletOutputStream servletOutputStream) {
    return virtualField.get(servletOutputStream);
  }

  private ServletOutputStreamInjectionState() {}
}
