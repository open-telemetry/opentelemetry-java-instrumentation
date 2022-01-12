/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Override
  public String transport(Request request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request, @Nullable Response response) {
    return request.getHost();
  }

  @Override
  @Nullable
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.getPort();
  }

  @Override
  @Nullable
  public String peerIp(Request request, @Nullable Response response) {
    // Return null unless the library supports resolution to something similar to SocketAddress
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/3012/files#r633188645
    return null;
  }
}
