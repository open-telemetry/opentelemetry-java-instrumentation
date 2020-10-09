/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;

public abstract class LettuceAbstractDatabaseClientTracer<QUERY>
    extends DatabaseClientTracer<RedisURI, QUERY> {
  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.lettuce-5.0";
  }

  @Override
  protected String dbSystem(RedisURI connection) {
    return DbSystem.REDIS;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisURI redisURI) {
    return new InetSocketAddress(redisURI.getHost(), redisURI.getPort());
  }

  @Override
  public Span onConnection(Span span, RedisURI connection) {
    if (connection != null && connection.getDatabase() != 0) {
      span.setAttribute(SemanticAttributes.REDIS_DATABASE_INDEX, connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }
}
