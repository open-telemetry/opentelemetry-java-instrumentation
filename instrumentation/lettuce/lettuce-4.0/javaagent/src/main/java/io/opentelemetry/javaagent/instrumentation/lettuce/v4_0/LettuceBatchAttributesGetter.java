/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class LettuceBatchAttributesGetter implements DbClientAttributesGetter<LettuceBatchRequest, Void> {

  @Override
  public String getDbSystemName(LettuceBatchRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(LettuceBatchRequest request) {
    Integer database = request.getDatabase();
    return database == null ? null : String.valueOf(database);
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getDbName(LettuceBatchRequest request) {
    // old semconv reports the redis database index as db.redis.database_index, not db.name
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(LettuceBatchRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(LettuceBatchRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(LettuceBatchRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getServerAddress(LettuceBatchRequest request) {
    InetSocketAddress serverAddress = request.getServerAddress();
    return serverAddress != null ? serverAddress.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(LettuceBatchRequest request) {
    InetSocketAddress serverAddress = request.getServerAddress();
    return serverAddress != null ? serverAddress.getPort() : null;
  }
}
