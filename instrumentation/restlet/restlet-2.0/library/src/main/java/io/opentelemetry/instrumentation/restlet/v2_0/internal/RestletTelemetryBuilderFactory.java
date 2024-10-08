/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import org.restlet.Request;
import org.restlet.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class RestletTelemetryBuilderFactory {
  private RestletTelemetryBuilderFactory() {}

  public static DefaultHttpServerInstrumenterBuilder<Request, Response> create(
      OpenTelemetry openTelemetry) {
    return DefaultHttpServerInstrumenterBuilder.create(
        "io.opentelemetry.restlet-2.0",
        openTelemetry,
        RestletHttpAttributesGetter.INSTANCE,
        new RestletHeadersGetter());
  }
}
