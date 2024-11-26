/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JavaHttpClientInstrumenterBuilderFactory {
  private JavaHttpClientInstrumenterBuilderFactory() {}

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-client";

  public static DefaultHttpClientInstrumenterBuilder<HttpRequest, HttpResponse<?>> create(
      OpenTelemetry openTelemetry) {
    return DefaultHttpClientInstrumenterBuilder.create(
        INSTRUMENTATION_NAME, openTelemetry, JavaHttpClientAttributesGetter.INSTANCE);
  }
}
