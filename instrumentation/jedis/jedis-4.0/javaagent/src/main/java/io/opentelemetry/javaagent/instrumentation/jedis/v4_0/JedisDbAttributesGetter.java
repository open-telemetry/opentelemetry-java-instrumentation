/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.REDIS;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest> {

  @Deprecated
  @Override
  public String getSystem(JedisRequest request) {
    return REDIS;
  }

  @Override
  public String getDbSystem(JedisRequest jedisRequest) {
    return REDIS;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(JedisRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getName(JedisRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(JedisRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getConnectionString(JedisRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getStatement(JedisRequest request) {
    return request.getStatement();
  }

  @Nullable
  @Override
  public String getDbQueryText(JedisRequest request) {
    return request.getStatement();
  }

  @Deprecated
  @Override
  public String getOperation(JedisRequest request) {
    return request.getOperation();
  }

  @Nullable
  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getOperation();
  }
}
