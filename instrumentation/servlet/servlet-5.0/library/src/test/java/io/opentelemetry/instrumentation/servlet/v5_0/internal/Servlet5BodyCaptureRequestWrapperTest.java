/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;

class Servlet5BodyCaptureRequestWrapperTest {

  @Test
  void wrapsRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);

    HttpServletRequest result = Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(request, 10);

    assertThat(result).isInstanceOf(Servlet5BodyCaptureRequestWrapper.class);
  }

  @Test
  void doesNotWrapTwice() {
    HttpServletRequest wrapper =
        Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(mock(HttpServletRequest.class), 10);

    assertThat(Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(wrapper, 10)).isSameAs(wrapper);
  }

  @Test
  void doesNotWrapWhenHiddenBehindOtherWrappers() {
    HttpServletRequest wrapper =
        Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(mock(HttpServletRequest.class), 10);
    HttpServletRequest applicationWrapper =
        new HttpServletRequestWrapper(new HttpServletRequestWrapper(wrapper));

    assertThat(Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(applicationWrapper, 10))
        .isSameAs(applicationWrapper);
  }

  @Test
  void wrapsOriginalRequestAgain() {
    // after an async dispatch the container gives the original request to the dispatched servlet
    HttpServletRequest original = mock(HttpServletRequest.class);
    Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(original, 10);

    HttpServletRequest result = Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(original, 10);

    assertThat(result).isInstanceOf(Servlet5BodyCaptureRequestWrapper.class);
  }

  @Test
  void wrapsRequestWrappedByOthers() {
    HttpServletRequest applicationWrapper =
        new HttpServletRequestWrapper(mock(HttpServletRequest.class));

    HttpServletRequest result =
        Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(applicationWrapper, 10);

    assertThat(result).isInstanceOf(Servlet5BodyCaptureRequestWrapper.class);
    assertThat(result).isNotSameAs(applicationWrapper);
  }
}
