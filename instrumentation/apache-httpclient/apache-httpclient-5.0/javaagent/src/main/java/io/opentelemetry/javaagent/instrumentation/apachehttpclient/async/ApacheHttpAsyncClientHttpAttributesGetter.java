/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.async;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.async.ApacheHttpClientRequest.headersToList;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;

final class ApacheHttpAsyncClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {
  private static final Logger logger =
      Logger.getLogger(ApacheHttpAsyncClientHttpAttributesGetter.class.getName());

  @Override
  public String method(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  public String url(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  public List<String> requestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Long requestContentLength(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.requestContentLength();
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(ApacheHttpClientRequest request, HttpResponse response) {
    return response.getCode();
  }

  @Override
  @Nullable
  public String flavor(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    if (response == null) {
      return null;
    }
    ProtocolVersion protocolVersion = response.getVersion();
    String protocol = protocolVersion.getProtocol();
    if (!protocol.equals("HTTP")) {
      return null;
    }
    int major = protocolVersion.getMajor();
    int minor = protocolVersion.getMinor();
    if (major == 1 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    if (major == 1 && minor == 1) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
    if (major == 2 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    logger.log(FINE, "unexpected http protocol version: {0}", protocolVersion);
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLength(ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return headersToList(response.getHeaders(name));
  }
}
