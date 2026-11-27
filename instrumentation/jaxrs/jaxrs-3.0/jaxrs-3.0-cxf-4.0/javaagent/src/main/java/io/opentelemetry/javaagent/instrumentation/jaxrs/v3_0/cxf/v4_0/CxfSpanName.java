/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.cxf.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.common.JaxrsPathUtil.normalizePath;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.annotation.Nullable;
import org.apache.cxf.jaxrs.model.MethodInvocationInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.message.Message;

final class CxfSpanName {

  static void updateServerRoute(Message message, OperationResourceInfoStack matchedResources) {
    Context context = Context.current();
    StringBuilder route = new StringBuilder(basePath(context, message));
    // CXF matches the class template only at the root, then matches each operation in order.
    route.append(
        normalize(
            matchedResources
                .firstElement()
                .getMethodInfo()
                .getClassResourceInfo()
                .getURITemplate()));
    for (MethodInvocationInfo invocation : matchedResources) {
      route.append(normalize(invocation.getMethodInfo().getURITemplate()));
    }
    HttpServerRoute.update(
        context,
        HttpServerRouteSource.NESTED_CONTROLLER,
        route.length() == 0 ? "/" : route.toString());
  }

  private static String basePath(Context context, Message message) {
    String path = (String) message.get(Message.BASE_PATH);
    if (path == null) {
      path = ServletContextPath.prepend(context, JaxrsContextPath.prepend(context, ""));
    }
    return normalizePath(path);
  }

  private static String normalize(@Nullable URITemplate template) {
    return normalizePath(template == null ? null : template.getValue());
  }

  private CxfSpanName() {}
}
