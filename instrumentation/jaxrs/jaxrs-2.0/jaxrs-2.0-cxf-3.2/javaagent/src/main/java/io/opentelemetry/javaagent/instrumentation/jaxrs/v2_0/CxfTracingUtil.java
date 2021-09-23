/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsPathUtil.normalizePath;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.message.Exchange;

public final class CxfTracingUtil {

  private CxfTracingUtil() {}

  public static Context updateServerSpanName(Exchange exchange) {
    Context context = Context.current();
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return null;
    }

    OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
    ClassResourceInfo cri = ori.getClassResourceInfo();
    String name = getName(cri.getURITemplate(), ori.getURITemplate());
    if (name.isEmpty()) {
      return null;
    }

    serverSpan.updateName(
        ServletContextPath.prepend(context, JaxrsContextPath.prepend(context, name)));
    // mark span name as updated from controller to avoid JaxRsAnnotationsTracer updating it
    ServerSpanNaming.updateSource(context, ServerSpanNaming.Source.CONTROLLER);

    return JaxrsContextPath.init(context, JaxrsContextPath.prepend(context, name));
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
}
