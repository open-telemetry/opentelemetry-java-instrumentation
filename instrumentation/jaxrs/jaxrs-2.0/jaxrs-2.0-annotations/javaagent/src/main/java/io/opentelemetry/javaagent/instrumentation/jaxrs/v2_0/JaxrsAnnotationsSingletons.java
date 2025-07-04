/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.jaxrs.AsyncResponseData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsInstrumenterFactory;
import javax.ws.rs.container.AsyncResponse;

public final class JaxrsAnnotationsSingletons {

  private static final Instrumenter<HandlerData, Void> INSTANCE =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxrs-2.0-annotations");

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTANCE;
  }

  public static final VirtualField<AsyncResponse, AsyncResponseData> ASYNC_RESPONSE_DATA =
      VirtualField.find(AsyncResponse.class, AsyncResponseData.class);

  private JaxrsAnnotationsSingletons() {}
}
