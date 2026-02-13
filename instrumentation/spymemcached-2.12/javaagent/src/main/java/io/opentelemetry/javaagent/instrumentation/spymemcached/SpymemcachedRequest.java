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
      MemcachedConnection connection, String queryText, @Nullable MemcachedNode handlingNode) {
    return new AutoValue_SpymemcachedRequest(connection, queryText, handlingNode);
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getQueryText();

  @Nullable
  public abstract MemcachedNode getHandlingNode();

  public String getOperationName() {
    String queryText = getQueryText();
    if (queryText.startsWith("async")) {
      queryText = queryText.substring("async".length());
    }
    if (queryText.startsWith("CAS")) {
      // 'CAS' name is special, we have to lowercase whole name
      return "cas" + queryText.substring("CAS".length());
    }

    char[] chars = queryText.toCharArray();
    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
