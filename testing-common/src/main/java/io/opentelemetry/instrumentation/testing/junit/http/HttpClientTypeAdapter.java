/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import java.net.URI;
import java.util.Map;

public interface HttpClientTypeAdapter<REQUEST> {

  /**
   * Build the request to be passed to {@link #sendRequest(Object, String, URI, Map)}.
   *
   * <p>By splitting this step out separate from {@code sendRequest}, tests and re-execute the same
   * request a second time to verify that the traceparent header is not added multiple times to the
   * request, and that the last one wins. Tests will fail if the header shows multiple times.
   */
  REQUEST buildRequest(String method, URI uri, Map<String, String> headers) throws Exception;

  /**
   * Make the request and return the status code of the response synchronously. Some clients, e.g.,
   * HTTPUrlConnection only support synchronous execution without callbacks, and many offer a
   * dedicated API for invoking synchronously, such as OkHttp's execute method.
   */
  int sendRequest(REQUEST request, String method, URI uri, Map<String, String> headers)
      throws Exception;

  default void sendRequestWithCallback(
      REQUEST request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult)
      throws Exception {
    // Must be implemented if the caller will be testing async.
    throw new UnsupportedOperationException();
  }
}
