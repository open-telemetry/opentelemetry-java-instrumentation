/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1;

import io.opentelemetry.instrumentation.api.instrumenter.db.AerospikeSemanticAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class DbAttributesGetter implements DbClientAttributesGetter<AerospikeRequest> {

  @Override
  public String getSystem(AerospikeRequest request) {
    return AerospikeSemanticAttributes.DbSystemValues.AEROSPIKE;
  }

  @Override
  @Nullable
  public String getUser(AerospikeRequest request) {
    return null;
  }

  @Override
  public String getName(AerospikeRequest request) {
    return null;
  }

  @Override
  public String getConnectionString(AerospikeRequest request) {
    return null;
  }

  @Override
  public String getStatement(AerospikeRequest request) {
    return null;
  }

  @Override
  public String getOperation(AerospikeRequest request) {
    return request.getOperation();
  }
}
