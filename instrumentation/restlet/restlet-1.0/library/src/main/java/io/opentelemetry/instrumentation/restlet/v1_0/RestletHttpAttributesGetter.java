/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import static io.opentelemetry.instrumentation.restlet.v1_0.RestletHeadersGetter.getHeaders;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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

final class RestletHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String method(Request request) {
    return request.getMethod().toString();
  }

  @Override
  @Nullable
  public String target(Request request) {
    Reference ref = request.getOriginalRef();
    String path = ref.getPath();
    return ref.hasQuery() ? path + "?" + ref.getQuery() : path;
  }

  @Override
  @Nullable
  public String route(Request request) {
    return null;
  }

  @Override
  @Nullable
  public String scheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    Form headers = getHeaders(request);
    if (headers == null) {
      return Collections.emptyList();
    }
    return parametersToList(headers.subList(name, /* ignoreCase = */ true));
  }

  @Override
  @Nullable
  public Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  public String flavor(Request request) {
    String version = (String) request.getAttributes().get("org.restlet.http.version");
    switch (version) {
      case "HTTP/1.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case "HTTP/1.1":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case "HTTP/2.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      default:
        // fall through
    }
    return null;
  }

  @Override
  @Nullable
  public String serverName(Request request) {
    return null;
  }

  @Override
  public Integer statusCode(Request request, Response response) {
    return response.getStatus().getCode();
  }

  @Override
  @Nullable
  public Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    Form headers = getHeaders(response);
    if (headers == null) {
      return Collections.emptyList();
    }
    return parametersToList(headers.subList(name, /* ignoreCase = */ true));
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
