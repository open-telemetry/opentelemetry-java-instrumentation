/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SpymemcachedAttributeExtractor
    extends DbAttributesExtractor<SpymemcachedRequest, Object> {
  @Override
  protected String system(SpymemcachedRequest spymemcachedRequest) {
    return "memcached";
  }

  @Override
  protected @Nullable String user(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  protected @Nullable String name(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  protected @Nullable String connectionString(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  protected @Nullable String statement(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  protected @Nullable String operation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.dbOperation();
  }
}
