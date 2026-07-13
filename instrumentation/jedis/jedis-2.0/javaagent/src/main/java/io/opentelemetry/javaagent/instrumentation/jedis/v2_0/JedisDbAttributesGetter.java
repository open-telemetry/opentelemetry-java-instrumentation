/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  @Override
  public String getDbSystemName(JedisRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(JedisRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(JedisRequest request) {
    return request.getQueryText();
  }

  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(JedisRequest request) {
    return request.getBatchSize();
  }

  @Override
  public String getServerAddress(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }
}
