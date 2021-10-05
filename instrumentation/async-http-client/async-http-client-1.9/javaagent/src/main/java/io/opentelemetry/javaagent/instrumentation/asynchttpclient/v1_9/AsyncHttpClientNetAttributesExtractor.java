/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class AsyncHttpClientNetAttributesExtractor
    extends NetAttributesServerExtractor<Request, Response> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(Request request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer peerPort(Request request) {
    return request.getUri().getPort();
  }

  @Override
  @Nullable
  public String peerIp(Request request) {
    return null;
  }
}
