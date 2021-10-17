/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientNetAttributesExtractor
    extends NetClientAttributesExtractor<ClassicHttpRequest, HttpResponse> {

  private static final Logger logger =
      LoggerFactory.getLogger(ApacheHttpClientNetAttributesExtractor.class);

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
        logger.debug("no default port mapping for scheme: {}", scheme);
        return null;
    }
  }

  @Override
  @Nullable
  public String peerIp(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }
}
