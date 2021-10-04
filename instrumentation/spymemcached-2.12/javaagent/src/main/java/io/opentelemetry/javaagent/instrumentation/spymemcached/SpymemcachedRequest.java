/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import com.google.auto.value.AutoValue;
import net.spy.memcached.MemcachedConnection;

@AutoValue
public abstract class SpymemcachedRequest {

  public static SpymemcachedRequest create(MemcachedConnection connection, String statement) {
    return new AutoValue_SpymemcachedRequest(connection, statement);
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getStatement();

  public String dbOperation() {
    char[] chars =
        getStatement()
            .replaceFirst("^async", "")
            // 'CAS' name is special, we have to lowercase whole name
            .replaceFirst("^CAS", "cas")
            .toCharArray();

    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);

    return new String(chars);
  }
}
