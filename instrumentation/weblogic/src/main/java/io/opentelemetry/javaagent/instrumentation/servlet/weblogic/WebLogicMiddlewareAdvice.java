/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.weblogic;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;

public class WebLogicMiddlewareAdvice {
  private static final String CONTEXT_ATTRIBUTE_NAME = "otel.weblogic.attributes";
  public static final String REQUEST_ATTRIBUTE_NAME = "otel.middleware";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(@Advice.Argument(1) HttpServletRequest servletRequest) {
    WebLogicEntity.Request request = WebLogicEntity.Request.wrap(servletRequest);

    Map<?, ?> attributes = getMiddlewareAttributes(request.getContext());
    request.instance.setAttribute(REQUEST_ATTRIBUTE_NAME, attributes);
  }

  public static Map<?, ?> getMiddlewareAttributes(WebLogicEntity.Context context) {
    if (context.instance == null) {
      return null;
    }

    Object value = context.instance.getAttribute(CONTEXT_ATTRIBUTE_NAME);

    if (value instanceof Map<?, ?>) {
      return (Map<?, ?>) value;
    }

    Map<String, String> middleware = collectMiddlewareAttributes(context);
    context.instance.setAttribute(CONTEXT_ATTRIBUTE_NAME, middleware);
    return middleware;
  }

  private static Map<String, String> collectMiddlewareAttributes(WebLogicEntity.Context context) {
    WebLogicEntity.Bean applicationBean = context.getBean();
    WebLogicEntity.Bean webServerBean = context.getServer().getBean();
    WebLogicEntity.Bean serverBean = webServerBean.getParent();
    WebLogicEntity.Bean clusterBean = WebLogicEntity.Bean.wrap(serverBean.getAttribute("Cluster"));
    WebLogicEntity.Bean domainBean = serverBean.getParent();

    Map<String, String> attributes = new HashMap<>();
    attributes.put("middleware.name", "WebLogic Server");
    attributes.put("middleware.version", detectVersion(context));
    attributes.put("middleware.weblogic.domain", domainBean.getName());
    attributes.put("middleware.weblogic.cluster", clusterBean.getName());
    attributes.put("middleware.weblogic.server", webServerBean.getName());
    attributes.put("middleware.weblogic.application", applicationBean.getName());

    return attributes;
  }

  private static String detectVersion(WebLogicEntity.Context context) {
    String serverInfo = context.instance.getServerInfo();

    if (serverInfo != null) {
      for (String token : serverInfo.split(" ")) {
        if (token.length() > 0 && Character.isDigit(token.charAt(0))) {
          return token;
        }
      }
    }

    return "";
  }
}
