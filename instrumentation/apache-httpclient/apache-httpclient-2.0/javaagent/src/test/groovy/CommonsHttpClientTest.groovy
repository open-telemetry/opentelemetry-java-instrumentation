/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.commons.httpclient.HttpMethod

class CommonsHttpClientTest extends AbstractCommonsHttpClientTest {

  @Override
  int sendRequest(HttpMethod request, String method, URI uri, Map<String, String> headers) {
    try {
      getClient(uri).executeMethod(request)
      return request.getStatusCode()
    } finally {
      request.releaseConnection()
    }
  }
}
