/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.annotation.Nullable;

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
