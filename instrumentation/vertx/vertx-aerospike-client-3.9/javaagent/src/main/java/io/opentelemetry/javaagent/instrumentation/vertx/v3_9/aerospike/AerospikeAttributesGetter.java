/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

public enum AerospikeAttributesGetter
    implements DbClientAttributesGetter<AerospikeRequest, Void> {
  INSTANCE;

  @Override
  public String getDbSystem(AerospikeRequest request) {
    return "aerospike";
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(AerospikeRequest request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getDbNamespace(AerospikeRequest request) {
    if (SemconvStability.emitStableDatabaseSemconv()) {
      return request.getDbNamespace();
    }
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(AerospikeRequest request) {
    return request.getConnectionString();
  }

  @Override
  @Nullable
  public String getDbQueryText(AerospikeRequest request) {
    // Aerospike doesn't have query text like SQL/Redis
    // We can compose operation + namespace + set for better visibility
    StringBuilder queryText = new StringBuilder(request.getOperation());
    if (request.getNamespace() != null) {
      queryText.append(" ").append(request.getNamespace());
    }
    if (request.getSetName() != null) {
      queryText.append(".").append(request.getSetName());
    }
    return queryText.toString();
  }

  @Nullable
  @Override
  public String getDbOperationName(AerospikeRequest request) {
    return request.getOperation();
  }
}

