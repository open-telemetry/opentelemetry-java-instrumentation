/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_8;

import com.ning.http.client.Request;
import io.opentelemetry.javaagent.instrumentation.asynchttpclient.common.AsyncHttpClientHelper;
import java.net.MalformedURLException;
import javax.annotation.Nullable;

final class AsyncHttpClient18Helper implements AsyncHttpClientHelper {

  static final AsyncHttpClient18Helper INSTANCE = new AsyncHttpClient18Helper();

  private AsyncHttpClient18Helper() {}

  @Override
  @Nullable
  public String getUrlFull(Request request) {
    try {
      return request.getURI().toURL().toString();
    } catch (MalformedURLException e) {
      return null;
    }
  }

  @Override
  public String getServerAddress(Request request) {
    return request.getOriginalURI().getHost();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.getOriginalURI().getPort();
  }

  @Override
  public void setHeader(Request request, String key, String value) {
    request.getHeaders().replace(key, value);
  }
}
