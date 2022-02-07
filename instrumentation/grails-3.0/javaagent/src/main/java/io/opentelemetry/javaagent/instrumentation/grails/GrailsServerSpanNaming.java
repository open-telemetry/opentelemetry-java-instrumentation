/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

public class GrailsServerSpanNaming {

  public static final HttpRouteGetter<GrailsControllerUrlMappingInfo> SERVER_SPAN_NAME =
      (context, info) -> {
        String action =
            info.getActionName() != null
                ? info.getActionName()
                : info.getControllerClass().getDefaultAction();
        // this is not the actual route/mapping, but it's the best thing that we have access to
        return ServletContextPath.prepend(context, "/" + info.getControllerName() + "/" + action);
      };

  private GrailsServerSpanNaming() {}
}
