/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_0;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_1;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JettyClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<Request, Response> {
  private static final Logger logger =
      LoggerFactory.getLogger(JettyClientHttpAttributesExtractor.class);

  // TODO: add support for capturing HTTP headers in library instrumentations
  JettyClientHttpAttributesExtractor() {
    super(CapturedHttpHeaders.empty());
  }

  @Override
  @Nullable
  protected String method(Request request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  protected String url(Request request) {
    return request.getURI().toString();
  }

  @Override
  @Nullable
  protected String userAgent(Request request) {
    HttpField agentField = request.getHeaders().getField(HttpHeader.USER_AGENT);
    return agentField != null ? agentField.getValue() : null;
  }

  @Override
  protected List<String> requestHeader(Request request, String name) {
    return request.getHeaders().getValuesList(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request request, @Nullable Response response) {
    HttpField requestContentLengthField = request.getHeaders().getField(HttpHeader.CONTENT_LENGTH);
    return getLongFromJettyHttpField(requestContentLengthField);
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected String flavor(Request request, @Nullable Response response) {

    if (response == null) {
      return HTTP_1_1;
    }
    HttpVersion httpVersion = response.getVersion();
    httpVersion = (httpVersion != null) ? httpVersion : HttpVersion.HTTP_1_1;
    switch (httpVersion) {
      case HTTP_0_9:
      case HTTP_1_0:
        return HTTP_1_0;
      case HTTP_1_1:
        return HTTP_1_1;
      default:
        // version 2.0 enum name difference in later versions 9.2 and 9.4 versions
        if (httpVersion.toString().endsWith("2.0")) {
          return HTTP_2_0;
        }

        return HTTP_1_1;
    }
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.getStatus();
  }

  @Override
  @Nullable
  protected Long responseContentLength(Request request, Response response) {
    Long respContentLength = null;
    if (response != null) {
      HttpField requestContentLengthField =
          response.getHeaders().getField(HttpHeader.CONTENT_LENGTH);
      respContentLength = getLongFromJettyHttpField(requestContentLengthField);
    }
    return respContentLength;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(Request request, Response response, String name) {
    return response.getHeaders().getValuesList(name);
  }

  private static Long getLongFromJettyHttpField(HttpField httpField) {
    Long longFromField = null;
    try {
      longFromField = httpField != null ? Long.getLong(httpField.getValue()) : null;
    } catch (NumberFormatException t) {
      logger.debug(
          "Value {} is not  not valid number format for header field: {}",
          httpField.getValue(),
          httpField.getName());
    }
    return longFromField;
  }
}
