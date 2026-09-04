/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.util.Iterator;
import javax.annotation.Nullable;

// Parsing the connection string rather than resolved nodes preserves configured endpoint order and
// the canonical DNS SRV identity while excluding credentials, paths, parameters, and fragments.
public class CouchbaseConnectionStrings {

  @Nullable
  public static CouchbaseServerTarget target(@Nullable String connectionString) {
    if (connectionString == null || connectionString.isEmpty()) {
      return null;
    }
    try {
      return target(ConnectionString.create(connectionString));
    } catch (RuntimeException ignored) {
      // Leave the target unavailable when the driver rejects the connection string.
      return null;
    }
  }

  @Nullable
  public static CouchbaseServerTarget target(@Nullable ConnectionString connectionString) {
    if (connectionString == null) {
      return null;
    }
    try {
      String scheme = connectionString.scheme().toString();
      if (connectionString.isValidDnsSrv()) {
        Iterator<ConnectionString.UnresolvedSocket> seeds = connectionString.hosts().iterator();
        if (!seeds.hasNext()) {
          return null;
        }
        ConnectionString.UnresolvedSocket seed = seeds.next();
        if (seed == null || seeds.hasNext()) {
          return null;
        }
        return CouchbaseServerTarget.forServiceDiscovery(scheme, seed.hostname());
      }
      DbServerTargetBuilder target =
          DbServerTarget.builder(CouchbaseServerTarget.defaultPort(scheme)).setSorted(false);
      for (ConnectionString.UnresolvedSocket seed : connectionString.hosts()) {
        if (seed == null) {
          target.addEndpoint(null, -1);
        } else {
          target.addEndpoint(
              CouchbaseServerTarget.cleanHost(seed.hostname()),
              seed.port() > 0 ? seed.port() : -1);
        }
      }
      return CouchbaseServerTarget.direct(target.build());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private CouchbaseConnectionStrings() {}
}
