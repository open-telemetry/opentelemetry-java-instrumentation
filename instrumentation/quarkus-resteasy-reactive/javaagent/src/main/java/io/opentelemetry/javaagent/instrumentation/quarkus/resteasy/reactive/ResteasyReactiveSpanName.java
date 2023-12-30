/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsPathUtil.normalizePath;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.mapping.RuntimeResource;
import org.jboss.resteasy.reactive.server.mapping.URITemplate;

final class ResteasyReactiveSpanName {
  // remember previous path to handle sub path locators
  private static final VirtualField<ResteasyReactiveRequestContext, String> pathField =
      VirtualField.find(ResteasyReactiveRequestContext.class, String.class);

  public static final ResteasyReactiveSpanName INSTANCE = new ResteasyReactiveSpanName();

  void updateServerSpanName(ResteasyReactiveRequestContext requestContext) {
    Context context = Context.current();
    String jaxRsName = calculateJaxRsName(requestContext);
    HttpServerRoute.update(context, HttpServerRouteSource.NESTED_CONTROLLER, jaxRsName);
    pathField.set(requestContext, jaxRsName);
  }

  private static String calculateJaxRsName(ResteasyReactiveRequestContext requestContext) {
    RuntimeResource target = requestContext.getTarget();
    if (target == null) {
      return null;
    }
    URITemplate classPath = target.getClassPath();
    URITemplate path = target.getPath();
    String name = normalize(classPath) + normalize(path);
    if (name.isEmpty()) {
      return null;
    }
    String existingPath = pathField.get(requestContext);
    return existingPath == null || existingPath.isEmpty() ? name : existingPath + name;
  }

  private static String normalize(URITemplate uriTemplate) {
    if (uriTemplate == null) {
      return "";
    }

    return normalizePath(uriTemplate.template);
  }

  private ResteasyReactiveSpanName() {}
}
