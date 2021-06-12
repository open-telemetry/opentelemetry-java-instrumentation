/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

final class AsyncHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<Request, Response> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(Request request, @Nullable Response response) {
    return request.getUri().getHost();
  }

  @Override
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.getUri().getPort();
  }

  @Override
  @Nullable
  public String peerIp(Request request, @Nullable Response response) {
    return null;
  }
}
