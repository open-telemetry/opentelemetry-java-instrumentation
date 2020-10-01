/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.rediscala;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import java.net.InetSocketAddress;
import redis.RedisCommand;

public class RediscalaClientTracer
    extends DatabaseClientTracer<RedisCommand<?, ?>, RedisCommand<?, ?>> {

  public static final RediscalaClientTracer TRACER = new RediscalaClientTracer();

  @Override
  protected String normalizeQuery(RedisCommand redisCommand) {
    return spanNameForClass(redisCommand.getClass());
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
    return "io.opentelemetry.auto.rediscala-1.8";
  }
}
