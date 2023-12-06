/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributeGetter;
import javax.annotation.Nullable;

public class SpymemcachedAttributeGetter implements DbClientAttributeGetter<SpymemcachedRequest> {

  @Override
  public String getSystem(SpymemcachedRequest spymemcachedRequest) {
    return "memcached";
  }

  @Override
  @Nullable
  public String getUser(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getName(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getConnectionString(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getStatement(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getOperation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.dbOperation();
  }
}
