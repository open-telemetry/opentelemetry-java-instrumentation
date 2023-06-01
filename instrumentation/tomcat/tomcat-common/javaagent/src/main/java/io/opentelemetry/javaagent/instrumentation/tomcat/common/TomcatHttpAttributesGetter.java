/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatHelper.messageBytesToString;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;

public class TomcatHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String getHttpRequestMethod(Request request) {
    return messageBytesToString(request.method());
  }

  @Override
  @Nullable
  public String getUrlScheme(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    return schemeMessageBytes.isNull() ? "http" : messageBytesToString(schemeMessageBytes);
  }

  @Nullable
  @Override
  public String getUrlPath(Request request) {
    return messageBytesToString(request.requestURI());
  }

  @Nullable
  @Override
  public String getUrlQuery(Request request) {
    return messageBytesToString(request.queryString());
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return Collections.list(request.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return Collections.list(response.getMimeHeaders().values(name));
  }
}
