/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import javax.annotation.Nullable;

// Parsing the connection string rather than resolved nodes preserves a DNS SRV hostname and
// excludes
// credentials, bucket paths, parameters, and fragments from the target.
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
      CouchbaseServerTarget.Builder target =
          CouchbaseServerTarget.builder(connectionString.scheme().toString());
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
