/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import javax.annotation.Nullable;

/**
 * Helper class for Aerospike instrumentation.
 * Separated to avoid muzzle scanning TypeInstrumentation framework classes.
 */
public final class AerospikeInstrumentationHelper {

  @Nullable
  public static AerospikeRequest createRequest(String operation, Object key) {
    if (key == null) {
      return null;
    }

    String namespace = null;
    String setName = null;

    if (key instanceof com.aerospike.client.Key) {
      com.aerospike.client.Key aerospikeKey = (com.aerospike.client.Key) key;
      namespace = aerospikeKey.namespace;
      setName = aerospikeKey.setName;
    }

    return new AerospikeRequest(operation, namespace, setName, null, null);
  }

  private AerospikeInstrumentationHelper() {}
}

