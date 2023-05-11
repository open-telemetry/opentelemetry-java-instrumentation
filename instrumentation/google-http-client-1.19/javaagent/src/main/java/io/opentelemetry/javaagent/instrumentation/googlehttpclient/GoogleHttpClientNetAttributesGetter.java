/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

final class GoogleHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  @Nullable
  public String getPeerName(HttpRequest request) {
    return request.getUrl().getHost();
  }

  @Override
  public Integer getPeerPort(HttpRequest request) {
    return request.getUrl().getPort();
  }
}
