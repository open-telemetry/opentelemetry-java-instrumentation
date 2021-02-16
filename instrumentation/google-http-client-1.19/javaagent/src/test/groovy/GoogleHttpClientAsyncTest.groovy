/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import spock.lang.Timeout

@Timeout(5)
class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {
  @Override
  HttpResponse executeRequest(HttpRequest request) {
    return request.executeAsync().get()
  }
}
