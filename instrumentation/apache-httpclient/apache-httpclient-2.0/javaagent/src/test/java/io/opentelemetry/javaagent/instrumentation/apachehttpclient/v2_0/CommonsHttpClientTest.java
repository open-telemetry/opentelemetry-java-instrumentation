/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import java.net.URI;
import java.util.Map;
import org.apache.commons.httpclient.HttpMethod;

class CommonsHttpClientTest extends AbstractCommonsHttpClientTest {

  @Override
  public int sendRequest(HttpMethod request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    try {
      getClient(uri).executeMethod(request);
      return request.getStatusCode();
    } finally {
      request.releaseConnection();
    }
  }
}
