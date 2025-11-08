/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;

@AutoValue
public abstract class SpymemcachedRequest {

  public static SpymemcachedRequest create(
      MemcachedConnection connection, String statement, @Nullable MemcachedNode handlingNode) {
    return new AutoValue_SpymemcachedRequest(connection, statement, handlingNode);
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getStatement();

  @Nullable
  public abstract MemcachedNode getHandlingNode();

  public String dbOperation() {
    String statement = getStatement();
    if (statement.startsWith("async")) {
      statement = statement.substring("async".length());
    }
    if (statement.startsWith("CAS")) {
      // 'CAS' name is special, we have to lowercase whole name
      return "cas" + statement.substring("CAS".length());
    }

    char[] chars = statement.toCharArray();
    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
