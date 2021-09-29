/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import static io.opentelemetry.instrumentation.restlet.v1_0.RestletHeadersGetter.getHeaders;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.restlet.data.Parameter;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;

final class RestletHttpAttributesExtractor
    extends HttpServerAttributesExtractor<Request, Response> {

  // TODO: add support for capturing HTTP headers in library instrumentations
  RestletHttpAttributesExtractor() {
    super(CapturedHttpHeaders.empty());
  }

  @Override
  protected String method(Request request) {
    return request.getMethod().toString();
  }

  @Override
  protected @Nullable String target(Request request) {
    Reference ref = request.getOriginalRef();
    String path = ref.getPath();
    return ref.hasQuery() ? path + "?" + ref.getQuery() : path;
  }

  @Override
  protected String host(Request request) {
    Reference originalRef = request.getOriginalRef();
    return originalRef.getHostDomain() + ":" + originalRef.getHostPort();
  }

  @Override
  protected @Nullable String route(Request request) {
    return null;
  }

  @Override
  protected @Nullable String scheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Override
  protected @Nullable String userAgent(Request request) {
    return request.getClientInfo().getAgent();
  }

  @Override
  protected List<String> requestHeader(Request request, String name) {
    return getHeaders(request).subList(name, /* ignoreCase = */ true).stream()
        .map(Parameter::getValue)
        .collect(Collectors.toList());
  }

  @Override
  protected @Nullable Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected @Nullable String flavor(Request request) {
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
  protected @Nullable String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.getStatus().getCode();
  }

  @Override
  protected @Nullable Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(Request request, Response response, String name) {
    return getHeaders(response).subList(name, /* ignoreCase = */ true).stream()
        .map(Parameter::getValue)
        .collect(Collectors.toList());
  }
}
