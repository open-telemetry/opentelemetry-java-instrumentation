/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.commons.httpclient.HttpMethod
import spock.lang.IgnoreIf

//this test will be ignored if not executed with -PtestLatestDeps=true
//because the latest dependency commons-httpclient v3.1 allows a call to the executeMethod 
//with some null parameters like HttpClient.executeMethod(null, request, null)  
//but this construct is not allowed in commons-httpclient v2 that is used for regular otel testing
@IgnoreIf({ !Boolean.getBoolean("testLatestDeps") })
class CommonsHttpClientLatestDepsTest extends AbstractCommonsHttpClientTest {
 
  @Override
  int sendRequest(HttpMethod request, String method, URI uri, Map<String, String> headers) {
    try {      
      getClient(uri).executeMethod(null, request, null)      
      return request.getStatusCode()
    } finally {
      request.releaseConnection()
    }
  }
}
