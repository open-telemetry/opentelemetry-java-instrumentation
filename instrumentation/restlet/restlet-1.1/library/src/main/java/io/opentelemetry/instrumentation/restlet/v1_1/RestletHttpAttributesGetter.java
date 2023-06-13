/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import static io.opentelemetry.instrumentation.restlet.v1_1.RestletHeadersGetter.getHeaders;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.restlet.data.Form;
import org.restlet.data.Parameter;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.util.Series;

enum RestletHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.getMethod().toString();
  }

  @Override
  @Nullable
  public String getUrlScheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(Request request) {
    return request.getOriginalRef().getPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(Request request) {
    return request.getOriginalRef().getQuery();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    Form headers = getHeaders(request);
    if (headers == null) {
      return Collections.emptyList();
    }
    return parametersToList(headers.subList(name, /* ignoreCase= */ true));
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    Form headers = getHeaders(response);
    if (headers == null) {
      return Collections.emptyList();
    }
    return parametersToList(headers.subList(name, /* ignoreCase= */ true));
  }

  // minimize memory overhead by not using streams
  private static List<String> parametersToList(Series<Parameter> headers) {
    if (headers.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> stringHeaders = new ArrayList<>(headers.size());
    for (Parameter header : headers) {
      stringHeaders.add(header.getValue());
    }
    return stringHeaders;
  }
}
