/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.UriBuilder;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TomcatHttpAttributesExtractor extends HttpAttributesExtractor<Request, Response> {

  @Override
  protected String method(Request request) {
    return request.method().toString();
  }

  @Override
  protected String url(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    String scheme = schemeMessageBytes.isNull() ? "http" : schemeMessageBytes.toString();
    String host = request.serverName().toString();
    int serverPort = request.getServerPort();
    String path = request.requestURI().toString();
    String query = request.queryString().toString();

    return UriBuilder.uri(scheme, host, serverPort, path, query);
  }

  @Override
  protected @Nullable String target(Request request) {
    return null;
  }

  @Override
  protected @Nullable String host(Request request) {
    // return request.serverName().toString() + ":" + request.getServerPort();
    return null;
  }

  @Override
  protected @Nullable String scheme(Request request) {
    /*
    MessageBytes schemeMessageBytes = request.scheme();
    return schemeMessageBytes.isNull() ? "http" : schemeMessageBytes.toString();
     */
    return null;
  }

  @Override
  protected @Nullable String userAgent(Request request) {
    return request.getHeader("User-Agent");
  }

  @Override
  protected @Nullable Long requestContentLength(Request request, @Nullable Response response) {
    /*
    long contentLength = request.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
     */
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected @Nullable String flavor(Request request, @Nullable Response response) {
    String flavor = request.protocol().toString();
    if (flavor != null) {
      // remove HTTP/ prefix to comply with semantic conventions
      if (flavor.startsWith("HTTP/")) {
        flavor = flavor.substring("HTTP/".length());
      }
    }
    return flavor;
  }

  @Override
  protected @Nullable Integer statusCode(Request request, Response response) {
    return response.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(Request request, Response response) {
    /*
    long contentLength = response.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
     */
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  protected @Nullable String route(Request request) {
    return null;
  }

  @Override
  protected @Nullable String serverName(Request request, @Nullable Response response) {
    // return request.serverName().toString();
    return null;
  }
}
