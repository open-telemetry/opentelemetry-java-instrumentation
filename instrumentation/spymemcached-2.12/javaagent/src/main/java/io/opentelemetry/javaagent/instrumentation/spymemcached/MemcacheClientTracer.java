/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import net.spy.memcached.MemcachedConnection;

public class MemcacheClientTracer extends DatabaseClientTracer<MemcachedConnection, String> {
  private static final MemcacheClientTracer TRACER = new MemcacheClientTracer();

  public static MemcacheClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String dbSystem(MemcachedConnection memcachedConnection) {
    return "memcached";
  }

  @Override
  protected InetSocketAddress peerAddress(MemcachedConnection memcachedConnection) {
    return null;
  }

  @Override
  protected void onStatement(Span span, String statement) {
    span.setAttribute(SemanticAttributes.DB_OPERATION, statement);
  }

  @Override
  protected String normalizeQuery(String query) {
    char[] chars =
        query
            .replaceFirst("^async", "")
            // 'CAS' name is special, we have to lowercase whole name
            .replaceFirst("^CAS", "cas")
            .toCharArray();

    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);

    return new String(chars);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spymemcached";
  }
}
