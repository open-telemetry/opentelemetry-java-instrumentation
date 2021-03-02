/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import redis.RedisCommand;

public class RediscalaClientTracer
    extends DatabaseClientTracer<RedisCommand<?, ?>, RedisCommand<?, ?>, String> {

  private static final RediscalaClientTracer TRACER = new RediscalaClientTracer();

  public static RediscalaClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String sanitizeStatement(RedisCommand<?, ?> redisCommand) {
    return spanNameForClass(redisCommand.getClass());
  }

  @Override
  protected String spanName(
      RedisCommand<?, ?> connection, RedisCommand<?, ?> statement, String operation) {
    return operation;
  }

  @Override
  protected String dbSystem(RedisCommand<?, ?> redisCommand) {
    return DbSystemValues.REDIS;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Override
  protected String dbStatement(
      RedisCommand<?, ?> connection, RedisCommand<?, ?> command, String operation) {
    return operation;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rediscala-1.8";
  }
}
