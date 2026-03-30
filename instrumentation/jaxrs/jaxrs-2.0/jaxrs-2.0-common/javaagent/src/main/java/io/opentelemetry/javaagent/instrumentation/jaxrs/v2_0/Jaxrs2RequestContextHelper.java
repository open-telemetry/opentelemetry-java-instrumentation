/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import io.opentelemetry.javaagent.instrumentation.jaxrs.RequestContextHelper;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;

public final class Jaxrs2RequestContextHelper {
  @Nullable
  public static Context createOrUpdateAbortSpan(
      Instrumenter<HandlerData, Void> instrumenter,
      ContainerRequestContext requestContext,
      HandlerData handlerData) {

    requestContext.setProperty(JaxrsConstants.ABORT_HANDLED, true);
    return RequestContextHelper.createOrUpdateAbortSpan(instrumenter, handlerData);
  }

  private Jaxrs2RequestContextHelper() {}
}
