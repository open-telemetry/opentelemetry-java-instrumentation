/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;

public class TomcatHttpAttributesExtractor
    extends HttpServerAttributesExtractor<Request, Response> {

  @Override
  protected String method(Request request) {
    return request.method().toString();
  }

  @Override
  @Nullable
  protected String target(Request request) {
    String target = request.requestURI().toString();
    String queryString = request.queryString().toString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  @Nullable
  protected String scheme(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    return schemeMessageBytes.isNull() ? "http" : schemeMessageBytes.toString();
  }

  @Override
  protected List<String> requestHeader(Request request, String name) {
    return Collections.list(request.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request request, @Nullable Response response) {
    long contentLength = request.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected String flavor(Request request) {
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
  @Nullable
  protected Integer statusCode(Request request, Response response) {
    return response.getStatus();
  }

  @Override
  @Nullable
  protected Long responseContentLength(Request request, Response response) {
    long contentLength = response.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(Request request, Response response, String name) {
    return Collections.list(response.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  protected String route(Request request) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(Request request, @Nullable Response response) {
    return request.serverName().toString();
  }
}
