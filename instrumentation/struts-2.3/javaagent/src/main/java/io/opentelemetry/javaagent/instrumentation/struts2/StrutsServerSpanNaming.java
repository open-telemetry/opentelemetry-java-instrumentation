/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionProxy;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;

public class StrutsServerSpanNaming {

  public static Supplier<String> getServerSpanNameSupplier(
      Context context, ActionProxy actionProxy) {
    return () -> getServerSpanName(context, actionProxy);
  }

  private static String getServerSpanName(Context context, ActionProxy actionProxy) {
    // We take name from the config, because it contains the path pattern from the
    // configuration.
    String result = actionProxy.getConfig().getName();

    String actionNamespace = actionProxy.getNamespace();
    if (actionNamespace != null && !actionNamespace.isEmpty()) {
      if (actionNamespace.endsWith("/") || result.startsWith("/")) {
        result = actionNamespace + result;
      } else {
        result = actionNamespace + "/" + result;
      }
    }

    if (!result.startsWith("/")) {
      result = "/" + result;
    }

    return ServletContextPath.prepend(context, result);
  }
}
