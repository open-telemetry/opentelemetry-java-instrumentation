/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;

public class TapestryServerSpanNaming {

  public static void updateServerSpanName(String pageName) {
    Context parentContext = Context.current();
    ServerSpanNaming.updateServerSpanName(
        parentContext, CONTROLLER, () -> getServerSpanName(parentContext, pageName));
  }

  private static String getServerSpanName(Context context, String pageName) {
    if (pageName == null) {
      return null;
    }
    if (!pageName.isEmpty()) {
      pageName = "/" + pageName;
    }

    return ServletContextPath.prepend(context, pageName);
  }
}
