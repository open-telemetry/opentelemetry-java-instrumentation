/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import javax.annotation.Nullable;

public final class AerospikeRequest {
  private final String operation;
  private final String namespace;
  private final String setName;
  private final String host;
  private final Integer port;

  public AerospikeRequest(
      String operation,
      @Nullable String namespace,
      @Nullable String setName,
      @Nullable String host,
      @Nullable Integer port) {
    this.operation = operation;
    this.namespace = namespace;
    this.setName = setName;
    this.host = host;
    this.port = port;
  }

  public String getOperation() {
    return operation;
  }

  @Nullable
  public String getNamespace() {
    return namespace;
  }

  @Nullable
  public String getSetName() {
    return setName;
  }

  @Nullable
  public String getUser() {
    return null; // Not available in basic API
  }

  @Nullable
  public String getConnectionString() {
    return null;
  }

  @Nullable
  public String getHost() {
    return host;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  @Nullable
  public String getPeerAddress() {
    return host;
  }

  @Nullable
  public Integer getPeerPort() {
    return port;
  }

  @Nullable
  public String getDbNamespace() {
    return namespace;
  }

  @Nullable
  public String getCollectionName() {
    return setName;
  }
}

