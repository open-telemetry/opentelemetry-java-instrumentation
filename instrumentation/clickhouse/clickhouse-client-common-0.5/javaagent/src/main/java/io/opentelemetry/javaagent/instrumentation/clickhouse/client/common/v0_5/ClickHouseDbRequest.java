/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {

  public static ClickHouseDbRequest create(
      @Nullable String host,
      @Nullable Integer port,
      Endpoint peer,
      @Nullable String configuredHost,
      @Nullable Integer configuredPort,
      @Nullable String serverAddressGroup,
      @Nullable String namespace,
      String sql) {
    return new AutoValue_ClickHouseDbRequest(
        host, port, peer, configuredHost, configuredPort, serverAddressGroup, namespace, sql);
  }

  public static Endpoint endpoint(@Nullable String address, @Nullable Integer port) {
    return new Endpoint(address, port);
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  abstract Endpoint getPeer();

  @Nullable
  public final String getPeerAddress() {
    return getPeer().value.address;
  }

  @Nullable
  public final Integer getPeerPort() {
    return getPeer().value.port;
  }

  public final void setPeer(@Nullable String address, @Nullable Integer port) {
    getPeer().set(address, port);
  }

  @Nullable
  public abstract String getConfiguredHost();

  @Nullable
  public abstract Integer getConfiguredPort();

  @Nullable
  public abstract String getServerAddressGroup();

  @Nullable
  public abstract String getNamespace();

  public abstract String getSql();

  public static class Endpoint {
    private volatile EndpointValue value;

    private Endpoint(@Nullable String address, @Nullable Integer port) {
      value = new EndpointValue(address, port);
    }

    private void set(@Nullable String address, @Nullable Integer port) {
      value = new EndpointValue(address, port);
    }
  }

  private static class EndpointValue {
    @Nullable private final String address;
    @Nullable private final Integer port;

    private EndpointValue(@Nullable String address, @Nullable Integer port) {
      this.address = address;
      this.port = port;
    }
  }
}
