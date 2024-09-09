/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest> {

  @Override
  public String getSystem(JedisRequest request) {
    return DbIncubatingAttributes.DbSystemValues.REDIS;
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
  public String getNamespace(JedisRequest request) {
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
  public String getOperationName(JedisRequest request) {
    return request.getOperation();
  }
}
