/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

public class SpymemcachedAttributesGetter
    implements DbClientAttributesGetter<SpymemcachedRequest, Object> {

  @Override
  public String getDbSystemName(SpymemcachedRequest spymemcachedRequest) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.MEMCACHED;
  }

  @Override
  @Nullable
  public String getDbNamespace(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getOperationName();
  }
}
