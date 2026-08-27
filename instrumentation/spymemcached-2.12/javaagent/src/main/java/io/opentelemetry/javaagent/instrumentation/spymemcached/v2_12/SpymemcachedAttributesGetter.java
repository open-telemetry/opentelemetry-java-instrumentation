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
    return spymemcachedRequest.getStableOperationName();
  }

  @Override
  @SuppressWarnings("deprecation") // old database semconv still use db.operation
  public String getDbOperation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getOperationName();
  }

  @Override
  @Nullable
  public String getServerAddress(SpymemcachedRequest spymemcachedRequest) {
    // the frozen old conventions describe the node that answered instead, so the configured target
    // is reported only once stable conventions are on; when both are emitted the configured target
    // wins, because server.address and server.port are shared between the two conventions
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
    return target == null ? null : target.getPort();
  }
}
