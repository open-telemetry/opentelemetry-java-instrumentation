/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;
import static io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsSingletons.spanNameExtractor;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;

public class JaxWsServerSpanNaming {

  public static void updateServerSpanName(Context parentContext, JaxWsRequest request) {
    ServerSpanNaming.updateServerSpanName(
        parentContext, CONTROLLER, () -> getServerSpanName(request));
  }

  private static String getServerSpanName(JaxWsRequest request) {
    return spanNameExtractor().extract(request);
  }
}
