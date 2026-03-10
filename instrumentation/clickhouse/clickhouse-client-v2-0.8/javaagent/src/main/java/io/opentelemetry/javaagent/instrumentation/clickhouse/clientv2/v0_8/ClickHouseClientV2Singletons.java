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
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseInstrumenterFactory;

public final class ClickHouseClientV2Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v2-0.8";
  private static final Instrumenter<ClickHouseDbRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
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
    return INSTRUMENTER;
  }

  private static final VirtualField<Client, AddressAndPort> ADDRESS_AND_PORT =
      VirtualField.find(Client.class, AddressAndPort.class);

  public static AddressAndPort getAddressAndPort(Client client) {
    return ADDRESS_AND_PORT.get(client);
  }

  public static AddressAndPort setAddressAndPort(Client client, String endpoint) {
    AddressAndPort addressAndPort = new AddressAndPort();

    if (endpoint != null) {
      addressAndPort.setAddress(UrlParser.getHost(endpoint));
      addressAndPort.setPort(UrlParser.getPort(endpoint));
    }
    ADDRESS_AND_PORT.set(client, addressAndPort);

    return addressAndPort;
  }

  private ClickHouseClientV2Singletons() {}
}
