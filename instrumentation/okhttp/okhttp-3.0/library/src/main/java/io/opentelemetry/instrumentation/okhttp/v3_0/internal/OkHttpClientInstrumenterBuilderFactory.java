/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OkHttpClientInstrumenterBuilderFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";

  private OkHttpClientInstrumenterBuilderFactory() {}

  public static DefaultHttpClientInstrumenterBuilder<Interceptor.Chain, Response> create(
      OpenTelemetry openTelemetry) {
    return DefaultHttpClientInstrumenterBuilder.create(
        INSTRUMENTATION_NAME, openTelemetry, OkHttpAttributesGetter.INSTANCE);
  }
}
