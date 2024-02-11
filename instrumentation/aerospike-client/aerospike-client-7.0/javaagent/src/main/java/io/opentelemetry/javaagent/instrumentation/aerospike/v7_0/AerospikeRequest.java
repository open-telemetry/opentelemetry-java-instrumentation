/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import com.aerospike.client.Key;
import com.aerospike.client.cluster.Node;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class AerospikeRequest {
  private Node node;
  private Integer size;
  private Status status;

  public static AerospikeRequest create(String operation, Key key) {
    return new AutoValue_AerospikeRequest(
        operation, key.namespace, key.setName, key.userKey.toString());
  }

  public static AerospikeRequest create(String operation, String namespace, String set) {
    return new AutoValue_AerospikeRequest(operation, namespace, set, null);
  }

  public abstract String getOperation();

  public abstract String getNamespace();

  public abstract String getSet();

  @Nullable
  public abstract String getUserKey();

  public void setNode(Node node) {
    this.node = node;
  }

  public Node getNode() {
    return this.node;
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }
}
