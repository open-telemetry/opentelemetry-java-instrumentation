/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import ratpack.http.Request;
import ratpack.http.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RatpackServerInstrumenterBuilderFactory {

  private RatpackServerInstrumenterBuilderFactory() {}

  public static DefaultHttpServerInstrumenterBuilder<Request, Response> create(
      String instrumentationName, OpenTelemetry openTelemetry) {

    return DefaultHttpServerInstrumenterBuilder.create(
        instrumentationName,
        openTelemetry,
        RatpackHttpAttributesGetter.INSTANCE,
        RatpackGetter.INSTANCE);
  }
}
