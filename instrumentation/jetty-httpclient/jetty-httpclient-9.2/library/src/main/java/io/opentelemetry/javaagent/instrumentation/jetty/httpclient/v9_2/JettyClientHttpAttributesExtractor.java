/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyClientHttpAttributesExtractor extends HttpAttributesExtractor<Request, Response> {
  private static final Logger LOG =
      LoggerFactory.getLogger(JettyClientHttpAttributesExtractor.class);

  private static final String[] httpFlavors = {"0.9", "1.0", "1.1", "2.0"};

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
  protected String target(Request request) {
    String queryString = request.getQuery();
    return queryString != null ? request.getPath() + "?" + queryString : request.getPath();
  }

  @Override
  @Nullable
  protected String host(Request request) {
    return request != null ? request.getHost() : null;
  }

  @Override
  @Nullable
  protected String route(Request request) {
    return null;
  }

  @Override
  @Nullable
  protected String scheme(Request request) {
    return request != null ? request.getScheme() : null;
  }

  @Override
  @Nullable
  protected String userAgent(Request request) {
    if (request != null) {
      HttpField agentField = request.getHeaders().getField(HttpHeader.USER_AGENT);
      return agentField != null ? agentField.getValue() : null;
    }
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request request, @Nullable Response response) {
    Long reqContentLength = null;
    if (request != null) {
      HttpField requestContentLengthField =
          request.getHeaders().getField(HttpHeader.CONTENT_LENGTH);
      reqContentLength = getLongFromJettyHttpField(requestContentLengthField);
    }
    return reqContentLength;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected String flavor(Request request, @Nullable Response response) {

    if (response != null) {
      HttpVersion httpVersion = response.getVersion();
      for (String version : httpFlavors) {
        if (httpVersion != null && httpVersion.toString().endsWith(version)) {
          return version;
        }
      }
    }
    return "1.1";
  }

  @Override
  @Nullable
  protected String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected String clientIp(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Integer statusCode(Request request, Response response) {
    return response != null ? response.getStatus() : null;
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

  private static Long getLongFromJettyHttpField(HttpField httpField) {
    Long longFromField = null;
    try {
      longFromField = httpField != null ? Long.getLong(httpField.getValue()) : null;
    } catch (NumberFormatException t) {
      LOG.debug(
          "Value {} is not  not valid number format for header field: {}",
          httpField.getValue(),
          httpField.getName());
    }
    return longFromField;
  }
}
