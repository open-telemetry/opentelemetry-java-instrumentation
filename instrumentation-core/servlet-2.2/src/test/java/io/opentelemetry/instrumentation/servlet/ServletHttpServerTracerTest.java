/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

public class ServletHttpServerTracerTest {

  @Test
  void testGetSpanName_emptySpanName() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("");
    when(request.getMethod()).thenReturn("PUT");
    String spanName = ServletHttpServerTracer.getSpanName(request);
    assertThat(spanName).isEqualTo("HTTP PUT");
  }

  @Test
  void testGetSpanName_nullSpanName() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn(null);
    assertThatThrownBy(() -> ServletHttpServerTracer.getSpanName(request))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void testGetSpanName_nullContextPath() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/swizzler");
    when(request.getContextPath()).thenReturn(null);
    String spanName = ServletHttpServerTracer.getSpanName(request);
    assertThat(spanName).isEqualTo("/swizzler");
  }

  @Test
  void testGetSpanName_emptyContextPath() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/swizzler");
    when(request.getContextPath()).thenReturn("");
    String spanName = ServletHttpServerTracer.getSpanName(request);
    assertThat(spanName).isEqualTo("/swizzler");
  }

  @Test
  void testGetSpanName_slashContextPath() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/swizzler");
    when(request.getContextPath()).thenReturn("/");
    String spanName = ServletHttpServerTracer.getSpanName(request);
    assertThat(spanName).isEqualTo("/swizzler");
  }

  @Test
  void testGetSpanName_appendsSpanNameToContext() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/swizzler");
    when(request.getContextPath()).thenReturn("/path/to");
    String spanName = ServletHttpServerTracer.getSpanName(request);
    assertThat(spanName).isEqualTo("/path/to/swizzler");
  }
}
