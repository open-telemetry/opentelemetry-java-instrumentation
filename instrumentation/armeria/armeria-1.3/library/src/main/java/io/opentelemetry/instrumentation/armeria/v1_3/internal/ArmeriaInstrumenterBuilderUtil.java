/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ArmeriaInstrumenterBuilderUtil {
  private ArmeriaInstrumenterBuilderUtil() {}

  @Nullable
  private static Function<
          ArmeriaTelemetryBuilder,
          DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>>
      clientBuilderExtractor;

  @Nullable
  private static Function<
          ArmeriaTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>>
      serverBuilderExtractor;

  @Nullable
  public static Function<
          ArmeriaTelemetryBuilder,
          DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>>
      getClientBuilderExtractor() {
    return clientBuilderExtractor;
  }

  public static void setClientBuilderExtractor(
      Function<
              ArmeriaTelemetryBuilder,
              DefaultHttpClientInstrumenterBuilder<ClientRequestContext, RequestLog>>
          clientBuilderExtractor) {
    ArmeriaInstrumenterBuilderUtil.clientBuilderExtractor = clientBuilderExtractor;
  }

  @Nullable
  public static Function<
          ArmeriaTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>>
      getServerBuilderExtractor() {
    return serverBuilderExtractor;
  }

  public static void setServerBuilderExtractor(
      Function<
              ArmeriaTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<ServiceRequestContext, RequestLog>>
          serverBuilderExtractor) {
    ArmeriaInstrumenterBuilderUtil.serverBuilderExtractor = serverBuilderExtractor;
  }
}
