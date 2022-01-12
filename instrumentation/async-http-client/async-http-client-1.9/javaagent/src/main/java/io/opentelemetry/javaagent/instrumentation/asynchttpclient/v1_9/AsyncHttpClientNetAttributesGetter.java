/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class AsyncHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Override
  public String transport(Request request, @Nullable Response response) {
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
