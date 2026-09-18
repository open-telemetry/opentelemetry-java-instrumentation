/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.clickhouse.client;

import java.util.concurrent.CompletableFuture;

public final class TestClickHouseClient implements ClickHouseClient {
  private final ClickHouseConfig config = new ClickHouseConfig();
  private final CompletableFuture<ClickHouseResponse> response = new CompletableFuture<>();
  private ClickHouseRequest<?> request;

  @Override
  public CompletableFuture<ClickHouseResponse> execute(ClickHouseRequest<?> request) {
    this.request = request.seal();
    return response;
  }

  @Override
  public ClickHouseConfig getConfig() {
    return config;
  }

  @Override
  public void close() {}

  public void failOverTo(ClickHouseNode server) {
    ClickHouseNode currentServer = request.getServer();
    request.changeServer(currentServer, server);
  }

  public void complete() {
    response.complete(null);
  }
}
