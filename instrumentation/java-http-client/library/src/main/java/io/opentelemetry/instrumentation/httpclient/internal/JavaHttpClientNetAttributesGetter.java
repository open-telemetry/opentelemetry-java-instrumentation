/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JavaHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse<?>> {

  @Nullable
  @Override
  public String getProtocolName(HttpRequest request, @Nullable HttpResponse<?> response) {
    return "http";
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpRequest request, @Nullable HttpResponse<?> response) {
    HttpClient.Version version;
    if (response != null) {
      version = response.version();
    } else {
      version = request.version().orElse(null);
    }
    if (version == null) {
      return null;
    }
    switch (version) {
      case HTTP_1_1:
        return "1.1";
      case HTTP_2:
        return "2.0";
    }
    return null;
  }

  @Override
  @Nullable
  public String getPeerName(HttpRequest request) {
    return request.uri().getHost();
  }

  @Override
  @Nullable
  public Integer getPeerPort(HttpRequest request) {
    return request.uri().getPort();
  }
}
