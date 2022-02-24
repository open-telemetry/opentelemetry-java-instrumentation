/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {

  @Override
  protected HttpResponse sendRequest(HttpRequest request) throws Exception {
    return request.executeAsync().get();
  }
}
