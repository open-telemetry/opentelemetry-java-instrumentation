/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class RestletTelemetryBuilderFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-1.1";

  private RestletTelemetryBuilderFactory() {}

  public static DefaultHttpServerInstrumenterBuilder<Request, Response> create(
      OpenTelemetry openTelemetry) {
    return DefaultHttpServerInstrumenterBuilder.create(
        INSTRUMENTATION_NAME,
        openTelemetry,
        RestletHttpAttributesGetter.INSTANCE,
        RestletHeadersGetter.INSTANCE);
  }
}
