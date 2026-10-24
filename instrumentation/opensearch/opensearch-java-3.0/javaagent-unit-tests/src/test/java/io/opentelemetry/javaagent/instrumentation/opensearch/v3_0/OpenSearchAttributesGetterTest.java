/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;

class OpenSearchAttributesGetterTest {

  private final OpenSearchAttributesGetter getter = new OpenSearchAttributesGetter();
  private final OpenSearchRequest request = OpenSearchRequest.create("GET", null, null);

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 99, 200, 302, 404, 503})
  void responseStatusExcludesSentinelsAndSuccesses(int statusCode) {
    OpenSearchException error =
        new OpenSearchException(
            ErrorResponse.of(
                response ->
                    response.status(statusCode).error(cause -> cause.type("test").reason("test"))));

    assertStatus(error, statusCode);
    assertStatus(new CompletionException(error), statusCode);
  }

  private void assertStatus(Throwable error, int statusCode) {
    assertThat(getter.getDbResponseStatusCode(request, null, error))
        .isEqualTo(statusCode >= 400 ? Integer.toString(statusCode) : null);
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(statusCode >= 400 || statusCode < 100 ? Integer.toString(statusCode) : null);
  }

  @Test
  void omitsResponseStatusWithoutServerCode() {
    assertThat(getter.getDbResponseStatusCode(request, null, null)).isNull();
    assertThat(getter.getDbResponseStatusCode(request, null, new IllegalStateException("test")))
        .isNull();
  }
}
