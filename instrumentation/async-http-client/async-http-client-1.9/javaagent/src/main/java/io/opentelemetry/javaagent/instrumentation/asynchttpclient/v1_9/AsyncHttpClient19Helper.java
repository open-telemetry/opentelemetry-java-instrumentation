/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import io.opentelemetry.javaagent.instrumentation.asynchttpclient.common.AsyncHttpClientHelper;

final class AsyncHttpClient19Helper implements AsyncHttpClientHelper {

  static final AsyncHttpClient19Helper INSTANCE = new AsyncHttpClient19Helper();

  private AsyncHttpClient19Helper() {}

  @Override
  public String getUrlFull(Request request) {
    return request.getUri().toUrl();
  }

  @Override
  public String getServerAddress(Request request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.getUri().getPort();
  }

  @Override
  public void setHeader(Request request, String key, String value) {
    request.getHeaders().replaceWith(key, value);
  }
}
