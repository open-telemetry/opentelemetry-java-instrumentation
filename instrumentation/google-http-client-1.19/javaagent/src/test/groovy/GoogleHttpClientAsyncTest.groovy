/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse

class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {
  @Override
  HttpResponse sendRequest(HttpRequest request) {
    return request.executeAsync().get()
  }
}
