/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsPathUtil.normalizePath;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.annotation.Nullable;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;

public class JerseySpanName implements HttpServerRouteGetter<Request> {

  public static final JerseySpanName INSTANCE = new JerseySpanName();

  public void updateServerSpanName(Request request) {
    Context context = Context.current();
    HttpServerRoute.update(context, HttpServerRouteSource.NESTED_CONTROLLER, this, request);
  }

  @Override
  @Nullable
  public String get(Context context, Request request) {
    ContainerRequest containerRequest = (ContainerRequest) request;
    UriInfo uriInfo = containerRequest.getUriInfo();
    ExtendedUriInfo extendedUriInfo = (ExtendedUriInfo) uriInfo;
    return extendedUriInfo.getMatchedTemplates().stream()
        .map((uriTemplate) -> normalizePath(uriTemplate.getTemplate()))
        .reduce((a, b) -> b + a)
        .map(s -> ServletContextPath.prepend(context, JaxrsContextPath.prepend(context, s)))
        .orElse(null);
  }
}
