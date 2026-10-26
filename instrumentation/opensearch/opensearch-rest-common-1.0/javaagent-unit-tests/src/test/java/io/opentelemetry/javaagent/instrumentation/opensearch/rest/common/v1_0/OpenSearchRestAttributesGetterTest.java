/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenSearchRestAttributesGetterTest {

  private final OpenSearchRestAttributesGetter getter =
      new OpenSearchRestAttributesGetter(error -> null);
  private final OpenSearchRestRequest request = OpenSearchRestRequest.create("GET", null);

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 99, 100, 200, 201, 302, 404, 503})
  void responseStatusExcludesOnlyInvalidCodes(int statusCode) {
    OpenSearchRestResponse response = () -> statusCode;

    assertThat(getter.getDbResponseStatusCode(request, response, null))
        .isEqualTo(statusCode >= 100 ? Integer.toString(statusCode) : null);
    assertThat(getter.getErrorType(request, response, null))
        .isEqualTo(statusCode >= 400 || statusCode < 100 ? Integer.toString(statusCode) : null);
  }

  @Test
  void omitsResponseStatusWithoutResponse() {
    assertThat(getter.getDbResponseStatusCode(request, null, null)).isNull();
    assertThat(getter.getDbResponseStatusCode(request, null, new IllegalStateException("test")))
        .isNull();
  }

  @Test
  void responseStatusTakesPrecedenceOverExceptionStatus() {
    OpenSearchRestAttributesGetter getter = new OpenSearchRestAttributesGetter(error -> 503);
    OpenSearchRestResponse response = () -> 404;
    IllegalStateException error = new IllegalStateException("test");

    assertThat(getter.getDbResponseStatusCode(request, response, error)).isEqualTo("404");
    assertThat(getter.getErrorType(request, response, error)).isEqualTo("404");
  }

  @Test
  void responseStatusCanBeExtractedFromErrorIndependentlyOfErrorType() {
    OpenSearchRestAttributesGetter getter = new OpenSearchRestAttributesGetter(error -> 200);
    IllegalStateException error = new IllegalStateException("test");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("200");
    assertThat(getter.getErrorType(request, null, error)).isNull();
  }
}
