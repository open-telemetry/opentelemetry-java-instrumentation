/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import io.opentelemetry.instrumentation.api.instrumenter.DatabaseClientInstrumenter;
import io.opentelemetry.instrumentation.api.tracer.Tracer;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import java.net.InetSocketAddress;
import redis.RedisCommand;

public class RediscalaClientTracer
    extends DatabaseClientInstrumenter<RedisCommand<?, ?>, RedisCommand<?, ?>> {

  private static final RediscalaClientTracer TRACER = new RediscalaClientTracer();

  public static RediscalaClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String normalizeQuery(RedisCommand redisCommand) {
    return Tracer.spanNameForClass(redisCommand.getClass());
  }

  @Override
  protected String dbSystem(RedisCommand redisCommand) {
    return DbSystem.REDIS;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisCommand redisCommand) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rediscala";
  }
}
