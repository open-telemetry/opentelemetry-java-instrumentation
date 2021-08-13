/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;
import static io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsSingletons.spanNameExtractor;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import java.util.function.Supplier;

public class JaxWsServerSpanNaming {

  public static void updateServerSpanName(Context parentContext, JaxWsRequest request) {
    ServerSpanNaming.updateServerSpanName(
        parentContext, CONTROLLER, getServerSpanNameSupplier(parentContext, request));
  }

  private static Supplier<String> getServerSpanNameSupplier(Context context, JaxWsRequest request) {
    return () -> getServerSpanName(context, request);
  }

  private static String getServerSpanName(Context context, JaxWsRequest request) {
    return spanNameExtractor().extract(request);
  }
}
