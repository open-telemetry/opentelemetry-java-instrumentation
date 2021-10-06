/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClientNetAttributesExtractor
    extends NetServerAttributesExtractor<Request, Response> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request) {
    return request.getHost();
  }

  @Override
  @Nullable
  public Integer peerPort(Request request) {
    return request.getPort();
  }

  @Override
  @Nullable
  public String peerIp(Request request) {
    // Return null unless the library supports resolution to something similar to SocketAddress
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/3012/files#r633188645
    return null;
  }
}
