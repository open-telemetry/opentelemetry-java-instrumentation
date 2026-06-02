/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient.v1_19;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

class GoogleHttpClientTest extends AbstractGoogleHttpClientTest {

  @Override
  protected HttpResponse sendRequest(HttpRequest request) throws Exception {
    return request.execute();
  }
}
