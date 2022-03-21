/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_0;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_1;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;

enum JettyClientHttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {
  INSTANCE;

  private static final Logger logger =
      Logger.getLogger(JettyClientHttpAttributesGetter.class.getName());

  @Override
  @Nullable
  public String method(Request request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String url(Request request) {
    return request.getURI().toString();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return request.getHeaders().getValuesList(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(Request request, @Nullable Response response) {
    HttpField requestContentLengthField = request.getHeaders().getField(HttpHeader.CONTENT_LENGTH);
    return getLongFromJettyHttpField(requestContentLengthField);
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  public String flavor(Request request, @Nullable Response response) {

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
  public Integer statusCode(Request request, Response response) {
    return response.getStatus();
  }

  @Override
  @Nullable
  public Long responseContentLength(Request request, Response response) {
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
  public Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    return response.getHeaders().getValuesList(name);
  }

  private static Long getLongFromJettyHttpField(HttpField httpField) {
    Long longFromField = null;
    try {
      longFromField = httpField != null ? Long.getLong(httpField.getValue()) : null;
    } catch (NumberFormatException t) {
      if (logger.isLoggable(Level.FINE)) {
        logger.log(
            Level.FINE,
            "Value {0} is not valid number format for header field: {1}",
            new String[] {httpField.getValue(), httpField.getName()});
      }
    }
    return longFromField;
  }
}
