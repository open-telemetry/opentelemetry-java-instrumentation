/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ElasticsearchDbResponseStatusTest {

  @ParameterizedTest
  @ValueSource(ints = {99, 100, 200, 399, 400})
  void recordsAvailableResponseStatusCodes(int statusCode) {
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(null);
    ElasticsearchRestRequest request = searchRequest();
    Response response = mock(Response.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(statusLine.getStatusCode()).thenReturn(statusCode);
    when(response.getStatusLine()).thenReturn(statusLine);

    assertThat(getter.getDbResponseStatusCode(request, response, null))
        .isEqualTo(statusCode >= 100 ? Integer.toString(statusCode) : null);
    assertThat(getter.getErrorType(request, response, null))
        .isEqualTo(statusCode >= 400 ? Integer.toString(statusCode) : null);
  }

  @Test
  void omitsResponseStatusWithoutResponse() {
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(null);
    ElasticsearchRestRequest request = searchRequest();

    assertThat(getter.getDbResponseStatusCode(request, null, null)).isNull();
    assertThat(getter.getDbResponseStatusCode(request, null, new IOException("connection failed")))
        .isNull();
  }

  @Test
  void getsResponseStatusFromResponseException() throws IOException {
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(null);
    ElasticsearchRestRequest request = searchRequest();
    Response response = mock(Response.class);
    when(response.getStatusLine())
        .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 404, ""));
    ResponseException error = mock(ResponseException.class);
    when(error.getResponse()).thenReturn(response);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("404");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo("404");
  }

  private static ElasticsearchRestRequest searchRequest() {
    return ElasticsearchRestRequest.create(
        "POST",
        "/test-index/_search",
        new ElasticsearchEndpointDefinition("SEARCH", new String[] {"/{index}/_search"}, true),
        null);
  }
}
