/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import static io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientRequest.headersToList;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

final class ApacheHttpClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  ApacheHttpClientHttpAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  @Override
  protected String method(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  protected String url(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  protected List<String> requestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(ApacheHttpClientRequest request, HttpResponse response) {
    return response.getStatusLine().getStatusCode();
  }

  @Override
  @Nullable
  protected String flavor(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getFlavor();
  }

  @Override
  @Nullable
  protected Long responseContentLength(ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return headersToList(response.getHeaders(name));
  }
}
