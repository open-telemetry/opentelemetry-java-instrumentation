/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

public class SpymemcachedAttributesGetter implements DbClientAttributesGetter<SpymemcachedRequest> {

  @Deprecated
  @Override
  public String getSystem(SpymemcachedRequest spymemcachedRequest) {
    return "memcached";
  }

  @Override
  public String getDbSystem(SpymemcachedRequest spymemcachedRequest) {
    return "memcached";
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getName(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getStatement(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Nullable
  @Override
  public String getDbQueryText(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getOperation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.dbOperation();
  }

  @Nullable
  @Override
  public String getDbOperationName(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.dbOperation();
  }
}
