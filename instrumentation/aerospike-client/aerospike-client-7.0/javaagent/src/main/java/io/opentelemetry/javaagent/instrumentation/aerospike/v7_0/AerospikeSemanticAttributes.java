/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

public final class AerospikeSemanticAttributes {
  private AerospikeSemanticAttributes() {}

  public static final AttributeKey<String> AEROSPIKE_STATUS = stringKey("aerospike.status");
  public static final AttributeKey<Long> AEROSPIKE_ERROR_CODE = longKey("aerospike.error.code");
  public static final AttributeKey<String> AEROSPIKE_NAMESPACE = stringKey("aerospike.namespace");
  public static final AttributeKey<String> AEROSPIKE_SET_NAME = stringKey("aerospike.set.name");
  public static final AttributeKey<String> AEROSPIKE_USER_KEY = stringKey("aerospike.user.key");
  public static final AttributeKey<Long> AEROSPIKE_TRANSFER_SIZE =
      longKey("aerospike.transfer.size");

  public static final class DbSystemValues {
    public static final String AEROSPIKE = "aerospike";

    private DbSystemValues() {}
  }
}
