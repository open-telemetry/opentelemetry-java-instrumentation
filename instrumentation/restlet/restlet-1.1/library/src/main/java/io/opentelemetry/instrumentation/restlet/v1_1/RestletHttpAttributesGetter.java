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
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.util.Series;

enum RestletHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getMethod(Request request) {
    return request.getMethod().toString();
  }

  @Override
  @Nullable
  public String getTarget(Request request) {
    Reference ref = request.getOriginalRef();
    String path = ref.getPath();
    return ref.hasQuery() ? path + "?" + ref.getQuery() : path;
  }

  @Override
  @Nullable
  public String getScheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    Form headers = getHeaders(request);
    if (headers == null) {
      return Collections.emptyList();
    }
    return parametersToList(headers.subList(name, /* ignoreCase= */ true));
  }

  @Override
  public Integer getStatusCode(Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getResponseHeader(Request request, Response response, String name) {
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
