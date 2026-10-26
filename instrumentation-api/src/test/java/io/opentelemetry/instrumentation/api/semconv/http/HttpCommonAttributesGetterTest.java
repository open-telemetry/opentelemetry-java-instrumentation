/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyList;

import java.util.List;
import org.junit.jupiter.api.Test;

class HttpCommonAttributesGetterTest {

  private static final HttpCommonAttributesGetter<String, String> GETTER =
      new TestHttpCommonAttributesGetter();

  @Test
  void getErrorType_returnsExceptionClassName() {
    Throwable error = new IllegalStateException();

    assertThat(GETTER.getErrorType("request", null, error))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void getErrorType_returnsNullWithoutException() {
    assertThat(GETTER.getErrorType("request", null, null)).isNull();
  }

  private static class TestHttpCommonAttributesGetter
      implements HttpCommonAttributesGetter<String, String> {

    @Override
    public String getHttpRequestMethod(String request) {
      return "GET";
    }

    @Override
    public List<String> getHttpRequestHeader(String request, String name) {
      return emptyList();
    }

    @Override
    public Integer getHttpResponseStatusCode(String request, String response, Throwable error) {
      return null;
    }

    @Override
    public List<String> getHttpResponseHeader(String request, String response, String name) {
      return emptyList();
    }
  }
}
