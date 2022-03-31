/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum ServerEndpoint {
  SUCCESS("success", 200, "success"),
  REDIRECT("redirect", 302, "/redirected"),
  ERROR("error-status", 500, "controller error"), // "error" is a special path for some frameworks
  EXCEPTION("exception", 500, "controller exception"),
  NOT_FOUND("notFound", 404, "not found"),
  CAPTURE_HEADERS("captureHeaders", 200, "headers captured"),
  CAPTURE_PARAMETERS("captureParameters", 200, "parameters captured"),

  // TODO: add tests for the following cases:
  QUERY_PARAM("query?some=query", 200, "some=query"),
  // OkHttp never sends the fragment in the request, so these cases don't work.
  // FRAGMENT_PARAM("fragment#some-fragment", 200, "some-fragment"),
  // QUERY_FRAGMENT_PARAM("query/fragment?some=query#some-fragment", 200,
  // "some=query#some-fragment"),
  PATH_PARAM("path/123/param", 200, "123"),
  AUTH_REQUIRED("authRequired", 200, null),
  LOGIN("login", 302, null),
  AUTH_ERROR("basicsecured/endpoint", 401, null),
  INDEXED_CHILD("child", 200, "");

  public static final String ID_ATTRIBUTE_NAME = "test.request.id";
  public static final String ID_PARAMETER_NAME = "id";

  private final URI uriObj;
  private final String path;
  final String query;
  final String fragment;
  final int status;
  final String body;

  public String getQuery() {
    return query;
  }

  public String getFragment() {
    return fragment;
  }

  public int getStatus() {
    return status;
  }

  public String getBody() {
    return body;
  }

  ServerEndpoint(String uri, int status, String body) {
    this.uriObj = URI.create(uri);
    this.path = uriObj.getPath();
    this.query = uriObj.getQuery();
    this.fragment = uriObj.getFragment();
    this.status = status;
    this.body = body;
  }

  public String getPath() {
    return "/" + path;
  }

  public String rawPath() {
    return path;
  }

  URI resolvePath(URI address) {
    return address.resolve(path);
  }

  URI resolve(URI address) {
    return address.resolve(uriObj);
  }

  URI resolveWithoutFragment(URI address) throws URISyntaxException {
    URI uri = resolve(address);
    return new URI(
        uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), null);
  }

  /**
   * Populates custom test attributes for the {@link
   * io.opentelemetry.instrumentation.test.base.HttpServerTest#controller} span (which must be the
   * current span when this is called) based on URL parameters. Required for {@link #INDEXED_CHILD}.
   */
  public void collectSpanAttributes(ServerEndpoint.UrlParameterProvider parameterProvider) {
    if (this == INDEXED_CHILD) {
      String value = parameterProvider.getParameter(ID_PARAMETER_NAME);

      if (value != null) {
        Span.current().setAttribute(ID_ATTRIBUTE_NAME, Long.parseLong(value));
      }
    }
  }

  private static final Map<String, ServerEndpoint> PATH_MAP =
      Arrays.stream(values()).collect(Collectors.toMap(x -> x.getPath(), x -> x));

  public static ServerEndpoint forPath(String path) {
    return PATH_MAP.get(path);
  }

  public interface UrlParameterProvider {
    String getParameter(String name);
  }
}
