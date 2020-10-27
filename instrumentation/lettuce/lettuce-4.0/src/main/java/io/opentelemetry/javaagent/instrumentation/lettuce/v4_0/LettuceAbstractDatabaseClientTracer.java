/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;

public abstract class LettuceAbstractDatabaseClientTracer<QUERY>
    extends DatabaseClientTracer<RedisURI, QUERY> {

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
      span.setAttribute(SemanticAttributes.DB_REDIS_DATABASE_INDEX, connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.lettuce";
  }
}
