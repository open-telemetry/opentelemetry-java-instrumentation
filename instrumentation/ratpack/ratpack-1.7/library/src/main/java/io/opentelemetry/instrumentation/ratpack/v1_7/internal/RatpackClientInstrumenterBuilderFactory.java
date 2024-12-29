/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RatpackClientInstrumenterBuilderFactory {
  private RatpackClientInstrumenterBuilderFactory() {}

  public static DefaultHttpClientInstrumenterBuilder<RequestSpec, HttpResponse> create(
      String instrumentationName, OpenTelemetry openTelemetry) {

    return DefaultHttpClientInstrumenterBuilder.create(
        instrumentationName,
        openTelemetry,
        RatpackHttpClientAttributesGetter.INSTANCE,
        RequestHeaderSetter.INSTANCE);
  }
}
