/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import javax.annotation.Nullable;

public class SpymemcachedAttributeExtractor
    extends DbAttributesExtractor<SpymemcachedRequest, Object> {
  @Override
  protected String system(SpymemcachedRequest spymemcachedRequest) {
    return "memcached";
  }

  @Override
  @Nullable
  protected String user(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String name(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String connectionString(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String statement(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String operation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.dbOperation();
  }
}
