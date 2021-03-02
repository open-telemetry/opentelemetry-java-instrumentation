/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;

public abstract class LettuceAbstractDatabaseClientTracer<STATEMENT>
    extends DatabaseClientTracer<RedisURI, STATEMENT, String> {

  @Override
  protected String spanName(RedisURI connection, STATEMENT statement, String operation) {
    return operation;
  }

  @Override
  public void onConnection(SpanBuilder span, RedisURI connection) {
    if (connection != null && connection.getDatabase() != 0) {
      span.setAttribute(
          SemanticAttributes.DB_REDIS_DATABASE_INDEX, (long) connection.getDatabase());
    }
    super.onConnection(span, connection);
  }

  @Override
  protected String dbSystem(RedisURI connection) {
    return DbSystemValues.REDIS;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisURI redisUri) {
    return new InetSocketAddress(redisUri.getHost(), redisUri.getPort());
  }

  @Override
  protected String dbStatement(RedisURI connection, STATEMENT statement, String operation) {
    return operation;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.lettuce-4.0";
  }
}
