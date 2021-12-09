/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

public class GrailsServerSpanNaming {

  public static final ServerSpanNameSupplier<GrailsControllerUrlMappingInfo> SERVER_SPAN_NAME =
      (context, info) -> {
        String action =
            info.getActionName() != null
                ? info.getActionName()
                : info.getControllerClass().getDefaultAction();
        return ServletContextPath.prepend(context, "/" + info.getControllerName() + "/" + action);
      };

  private GrailsServerSpanNaming() {}
}
