/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.R2dbcInstrumenterBuilder;
import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/** A builder of {@link R2dbcTelemetry}. */
public final class R2dbcTelemetryBuilder {

  private ConnectionFactory originalConnectionFactory;
  private ConnectionFactoryOptions connectionFactoryOptions;
  private final R2dbcInstrumenterBuilder instrumenterBuilder;

  R2dbcTelemetryBuilder(OpenTelemetry openTelemetry) {
    instrumenterBuilder = new R2dbcInstrumenterBuilder(openTelemetry);
  }

  @CanIgnoreReturnValue
  public R2dbcTelemetryBuilder setOriginalConnectionFactory(
      ConnectionFactory originalConnectionFactory) {
    this.originalConnectionFactory = originalConnectionFactory;
    return this;
  }

  @CanIgnoreReturnValue
  public R2dbcTelemetryBuilder setConnectionFactoryOptions(
      ConnectionFactoryOptions connectionFactoryOptions) {
    this.connectionFactoryOptions = connectionFactoryOptions;
    return this;
  }

  /**
   * Returns a new {@link R2dbcTelemetry} with the settings of this {@link R2dbcTelemetryBuilder}.
   */
  public R2dbcTelemetry build() {
    ConnectionFactory proxiedFactory =
        ProxyConnectionFactory.builder(originalConnectionFactory)
            .listener(new TraceProxyListener(instrumenterBuilder.build(), connectionFactoryOptions))
            .build();

    return new R2dbcTelemetry(proxiedFactory);
  }
}
