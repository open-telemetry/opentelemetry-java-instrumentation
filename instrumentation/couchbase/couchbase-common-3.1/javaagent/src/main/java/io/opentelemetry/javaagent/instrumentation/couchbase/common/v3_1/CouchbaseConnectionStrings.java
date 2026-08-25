/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import javax.annotation.Nullable;

/**
 * Reads the target a Couchbase 3.x client was configured with out of the connection string its core
 * was built from.
 *
 * <p>The driver hands over the seeds it parsed, so the rendered target never contains credentials,
 * a bucket, a path, connection string parameters or a fragment. A host that resolves through DNS
 * SRV is the lone seed of its connection string, so it is reported as itself rather than as the
 * seeds the driver looked up.
 */
public class CouchbaseConnectionStrings {

  /** The target {@code connectionString} names, or {@code null} when it cannot be read. */
  @Nullable
  public static CouchbaseServerTarget target(@Nullable String connectionString) {
    if (connectionString == null || connectionString.isEmpty()) {
      return null;
    }
    try {
      return target(ConnectionString.create(connectionString));
    } catch (RuntimeException ignored) {
      // a connection string the driver itself rejects leaves the client without a target
      return null;
    }
  }

  /** The target {@code connectionString} names, or {@code null} when it names none. */
  @Nullable
  public static CouchbaseServerTarget target(@Nullable ConnectionString connectionString) {
    if (connectionString == null) {
      return null;
    }
    try {
      CouchbaseServerTarget.Builder target = CouchbaseServerTarget.builder();
      for (ConnectionString.UnresolvedSocket seed : connectionString.hosts()) {
        if (seed == null) {
          target.addSeed(null, 0);
        } else {
          target.addSeed(seed.hostname(), seed.port());
        }
      }
      return target.build();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private CouchbaseConnectionStrings() {}
}
