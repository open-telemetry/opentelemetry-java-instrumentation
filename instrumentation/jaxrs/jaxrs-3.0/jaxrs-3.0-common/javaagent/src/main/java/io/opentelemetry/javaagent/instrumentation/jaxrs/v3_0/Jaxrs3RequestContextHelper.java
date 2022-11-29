/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import io.opentelemetry.javaagent.instrumentation.jaxrs.RequestContextHelper;
import jakarta.ws.rs.container.ContainerRequestContext;

public final class Jaxrs3RequestContextHelper {
  public static <T extends HandlerData> Context createOrUpdateAbortSpan(
      Instrumenter<T, Void> instrumenter, ContainerRequestContext requestContext, T handlerData) {

    if (handlerData == null) {
      return null;
    }

    requestContext.setProperty(JaxrsConstants.ABORT_HANDLED, true);
    return RequestContextHelper.createOrUpdateAbortSpan(instrumenter, handlerData);
  }

  private Jaxrs3RequestContextHelper() {}
}
