/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

class SpymemcachedAttributesGetter
    implements DbClientAttributesGetter<SpymemcachedRequest, Object> {

  @Override
  public String getDbSystemName(SpymemcachedRequest spymemcachedRequest) {
    return DbSystemNameIncubatingValues.MEMCACHED;
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
  public String getDbOperationName(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getOperationName();
  }

  @Override
  @Nullable
  public String getServerAddress(SpymemcachedRequest spymemcachedRequest) {
    // the old conventions describe the node that answered instead, and they are frozen
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    SpymemcachedServerTarget target = spymemcachedRequest.getServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(SpymemcachedRequest spymemcachedRequest) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    SpymemcachedServerTarget target = spymemcachedRequest.getServerTarget();
    // a target naming several nodes already carries the port of each of them
    return target == null ? null : target.getPort();
  }
}
