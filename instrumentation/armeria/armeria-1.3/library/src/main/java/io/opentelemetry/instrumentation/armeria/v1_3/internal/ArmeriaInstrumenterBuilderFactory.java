/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ArmeriaInstrumenterBuilderFactory {
  private ArmeriaInstrumenterBuilderFactory() {}

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.armeria-1.3";

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>
      getServerBuilder(OpenTelemetry openTelemetry) {
    return new DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            (HttpServerAttributesGetter) ArmeriaHttpServerAttributesGetter.INSTANCE)
        .setHeaderGetter(RequestContextGetter.INSTANCE);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>
      getClientBuilder(OpenTelemetry openTelemetry) {
    return new DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            (HttpClientAttributesGetter) ArmeriaHttpClientAttributesGetter.INSTANCE)
        .setHeaderSetter(ClientRequestContextSetter.INSTANCE);
  }
}
