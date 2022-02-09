/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;

public class TomcatHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String method(Request request) {
    return request.method().toString();
  }

  @Override
  @Nullable
  public String target(Request request) {
    String target = request.requestURI().toString();
    String queryString = request.queryString().toString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  @Nullable
  public String scheme(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    return schemeMessageBytes.isNull() ? "http" : schemeMessageBytes.toString();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return Collections.list(request.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  public Long requestContentLength(Request request, @Nullable Response response) {
    long contentLength = request.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  public String flavor(Request request) {
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
  public Integer statusCode(Request request, Response response) {
    return response.getStatus();
  }

  @Override
  @Nullable
  public Long responseContentLength(Request request, Response response) {
    long contentLength = response.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    return Collections.list(response.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  public String route(Request request) {
    return null;
  }

  @Override
  @Nullable
  public String serverName(Request request, @Nullable Response response) {
    return request.serverName().toString();
  }
}
