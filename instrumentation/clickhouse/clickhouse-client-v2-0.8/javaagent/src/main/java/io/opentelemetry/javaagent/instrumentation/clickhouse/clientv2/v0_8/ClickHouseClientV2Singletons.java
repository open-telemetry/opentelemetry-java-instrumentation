/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPort;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;

public class ClickHouseClientV2Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v2-0.8";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  static {
    instrumenter =
        ClickHouseInstrumenterFactory.createInstrumenter(
            INSTRUMENTER_NAME,
            error -> {
              if (error instanceof ServerException) {
                return Integer.toString(((ServerException) error).getCode());
              }
              return null;
            });
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static AddressAndPort getAddressAndPort(Client client) {
    AddressAndPort addressAndPort = new AddressAndPort();
    String endpoint = client.getEndpoints().stream().findFirst().orElse(null);
    if (endpoint != null) {
      addressAndPort.setAddress(UrlParser.getHost(endpoint));
      addressAndPort.setPort(UrlParser.getPort(endpoint));
    }
    return addressAndPort;
  }

  private ClickHouseClientV2Singletons() {}
}
