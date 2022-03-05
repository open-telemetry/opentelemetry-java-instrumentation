/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

public class SpymemcachedAttributesGetter implements DbClientAttributesGetter<SpymemcachedRequest> {

  @Override
  public String system(SpymemcachedRequest spymemcachedRequest) {
    return "memcached";
  }

  @Override
  @Nullable
  public String user(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String name(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String statement(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String operation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.dbOperation();
  }
}
