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
package io.opentelemetry.auto.instrumentation.rediscala;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Tracer;
import redis.RedisCommand;
import redis.protocol.RedisReply;

public class RediscalaClientDecorator
    extends DatabaseClientDecorator<RedisCommand<? extends RedisReply, ?>> {

  public static final RediscalaClientDecorator DECORATE = new RediscalaClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.rediscala-1.8");

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(final RedisCommand<? extends RedisReply, ?> session) {
    return null;
  }

  @Override
  protected String dbInstance(final RedisCommand<? extends RedisReply, ?> session) {
    return null;
  }
}
