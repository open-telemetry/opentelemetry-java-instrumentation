/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
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
      CouchbaseServerTarget.Builder target = CouchbaseServerTarget.builder(scheme);
      for (ConnectionString.UnresolvedSocket seed : connectionString.hosts()) {
        if (seed == null) {
          target.addSeed(null, 0);
        } else {
          target.addSeed(seed.hostname(), seed.port());
        }
      }
      return target.buildPreservingOrder();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private CouchbaseConnectionStrings() {}
}
