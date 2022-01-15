/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsPathUtil.normalizePath;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.server.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.api.server.ServerSpanNaming;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.message.Exchange;

public final class CxfSpanName implements ServerSpanNameSupplier<String> {

  public static final CxfSpanName INSTANCE = new CxfSpanName();

  public Context updateServerSpanName(Exchange exchange) {
    Context context = Context.current();
    String jaxrsName = calculateJaxrsName(context, exchange);

    ServerSpanNaming.updateServerSpanName(
        context, ServerSpanNaming.Source.NESTED_CONTROLLER, this, jaxrsName);

    return JaxrsContextPath.init(context, jaxrsName);
  }

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
  public String get(Context context, String jaxrsName) {
    return ServletContextPath.prepend(context, jaxrsName);
  }

  private CxfSpanName() {}
}
