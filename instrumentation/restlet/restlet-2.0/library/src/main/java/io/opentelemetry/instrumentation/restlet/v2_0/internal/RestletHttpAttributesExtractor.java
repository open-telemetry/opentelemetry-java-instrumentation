/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletHeadersGetter.getHeaders;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Reference;
import org.restlet.util.Series;

final class RestletHttpAttributesExtractor
    extends HttpServerAttributesExtractor<Request, Response> {

  RestletHttpAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  @Override
  protected String method(Request request) {
    return request.getMethod().toString();
  }

  @Override
  @Nullable
  protected String target(Request request) {
    Reference ref = request.getOriginalRef();
    String path = ref.getPath();
    return ref.hasQuery() ? path + "?" + ref.getQuery() : path;
  }

  @Override
  @Nullable
  protected String route(Request request) {
    return null;
  }

  @Override
  @Nullable
  protected String scheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Override
  protected List<String> requestHeader(Request request, String name) {
    Series<?> headers = getHeaders(request);
    if (headers == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(headers.getValuesArray(name, true));
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected String flavor(Request request) {
    switch (request.getProtocol().toString()) {
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
  protected String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.getStatus().getCode();
  }

  @Override
  @Nullable
  protected Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(Request request, Response response, String name) {
    Series<?> headers = getHeaders(response);
    if (headers == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(headers.getValuesArray(name, true));
  }
}
