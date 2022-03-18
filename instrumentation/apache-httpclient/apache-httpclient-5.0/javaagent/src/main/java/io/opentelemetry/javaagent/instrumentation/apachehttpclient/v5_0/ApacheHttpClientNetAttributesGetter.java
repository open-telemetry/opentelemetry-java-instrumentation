/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpResponse;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<ClassicHttpRequest, HttpResponse> {

  private static final Logger logger =
      Logger.getLogger(ApacheHttpClientNetAttributesGetter.class.getName());

  @Override
  public String transport(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return request.getAuthority().getHostName();
  }

  @Override
  public Integer peerPort(ClassicHttpRequest request, @Nullable HttpResponse response) {
    int port = request.getAuthority().getPort();
    if (port != -1) {
      return port;
    }
    String scheme = request.getScheme();
    if (scheme == null) {
      return 80;
    }
    switch (scheme) {
      case "http":
        return 80;
      case "https":
        return 443;
      default:
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("no default port mapping for scheme: " + scheme);
        }
        return null;
    }
  }

  @Override
  @Nullable
  public String peerIp(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }
}
