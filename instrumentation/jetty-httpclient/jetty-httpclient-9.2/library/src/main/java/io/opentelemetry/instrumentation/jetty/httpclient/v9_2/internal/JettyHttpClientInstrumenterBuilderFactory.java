/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JettyHttpClientInstrumenterBuilderFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-httpclient-9.2";

  private JettyHttpClientInstrumenterBuilderFactory() {}

  public static DefaultHttpClientInstrumenterBuilder<Request, Response> create(
      OpenTelemetry openTelemetry) {
    return DefaultHttpClientInstrumenterBuilder.create(
        INSTRUMENTATION_NAME,
        openTelemetry,
        JettyClientHttpAttributesGetter.INSTANCE,
        HttpHeaderSetter.INSTANCE);
  }
}
