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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JettyHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Override
  public String getTransport(Request request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(Request request, @Nullable Response response) {
    return "http";
  }

  @Nullable
  @Override
  public String getProtocolVersion(Request request, @Nullable Response response) {
    if (response == null || response.getVersion() == null) {
      return null;
    }
    String version = response.getVersion().toString();
    if (version.startsWith("HTTP/")) {
      version = version.substring("HTTP/".length());
    }
    return version;
  }

  @Override
  @Nullable
  public String getPeerName(Request request) {
    return request.getHost();
  }

  @Override
  @Nullable
  public Integer getPeerPort(Request request) {
    return request.getPort();
  }
}
