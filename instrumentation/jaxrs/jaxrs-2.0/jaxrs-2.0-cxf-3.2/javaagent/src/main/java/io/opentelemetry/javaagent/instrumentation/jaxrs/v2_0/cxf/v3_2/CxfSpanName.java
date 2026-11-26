/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.cxf.v3_2;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsPathUtil.normalizePath;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.annotation.Nullable;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.message.Exchange;

public class CxfSpanName implements HttpServerRouteGetter<String> {

  public static final CxfSpanName INSTANCE = new CxfSpanName();

  @Nullable
  public Context updateServerSpanName(Exchange exchange) {
    Context context = Context.current();
    String jaxrsName = calculateJaxrsName(context, exchange);

    HttpServerRoute.update(context, HttpServerRouteSource.NESTED_CONTROLLER, this, jaxrsName);

    return JaxrsContextPath.init(context, jaxrsName);
  }

  @Nullable
  private static String calculateJaxrsName(Context context, Exchange exchange) {
    OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
    ClassResourceInfo cri = ori.getClassResourceInfo();
    String name = getName(cri.getURITemplate(), ori.getURITemplate());
    if (name.isEmpty()) {
      return null;
    }
    return JaxrsContextPath.prepend(context, name);
  }

  private static String getName(URITemplate classTemplate, URITemplate operationTemplate) {
    String classPath = normalize(classTemplate);
    String operationPath = normalize(operationTemplate);

    return classPath + operationPath;
  }

  private static String normalize(URITemplate uriTemplate) {
    if (uriTemplate == null) {
      return "";
    }

    return normalizePath(uriTemplate.getValue());
  }

  @Override
  @Nullable
  public String get(Context context, @Nullable String jaxrsName) {
    return ServletContextPath.prepend(context, jaxrsName);
  }

  private CxfSpanName() {}
}
