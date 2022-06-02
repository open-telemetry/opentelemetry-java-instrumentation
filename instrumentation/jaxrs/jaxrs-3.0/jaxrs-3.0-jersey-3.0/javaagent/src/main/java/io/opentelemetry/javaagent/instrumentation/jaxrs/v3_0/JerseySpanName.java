/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsPathUtil.normalizePath;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import javax.annotation.Nullable;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;

public class JerseySpanName implements HttpRouteGetter<Request> {

  public static final JerseySpanName INSTANCE = new JerseySpanName();

  public void updateServerSpanName(Request request) {
    Context context = Context.current();
    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.NESTED_CONTROLLER, this, request);
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
