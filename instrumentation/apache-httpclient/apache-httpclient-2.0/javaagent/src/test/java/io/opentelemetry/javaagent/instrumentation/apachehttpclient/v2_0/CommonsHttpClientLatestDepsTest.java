/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import java.net.URI;
import java.util.Map;
import org.apache.commons.httpclient.HttpMethod;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// this test will be ignored if not executed with -PtestLatestDeps=true
// because the latest dependency commons-httpclient v3.1 allows a call to the executeMethod
// with some null parameters like HttpClient.executeMethod(null, request, null)
// but this construct is not allowed in commons-httpclient v2 that is used for regular otel testing
@EnabledIfSystemProperty(named = "testLatestDeps", matches = "true")
public class CommonsHttpClientLatestDepsTest extends AbstractCommonsHttpClientTest {
  @Override
  public int sendRequest(HttpMethod request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    try {
      getClient(uri).executeMethod(null, request, null);
      return request.getStatusCode();
    } finally {
      request.releaseConnection();
    }
  }
}
