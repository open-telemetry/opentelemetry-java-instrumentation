/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JdkHttpNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse<?>> {

  @Override
  public String getTransport(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String getPeerName(HttpRequest httpRequest) {
    return httpRequest.uri().getHost();
  }

  @Override
  @Nullable
  public Integer getPeerPort(HttpRequest httpRequest) {
    return httpRequest.uri().getPort();
  }
}
