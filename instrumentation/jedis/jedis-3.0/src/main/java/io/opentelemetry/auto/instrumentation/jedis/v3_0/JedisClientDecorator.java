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

package io.opentelemetry.auto.instrumentation.jedis.v3_0;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.DatabaseClientDecorator;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Tracer;
import redis.clients.jedis.Connection;

public class JedisClientDecorator extends DatabaseClientDecorator<Connection> {
  public static final JedisClientDecorator DECORATE = new JedisClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.jedis-1.4");

  @Override
  protected String dbSystem() {
    return DbSystem.REDIS;
  }

  @Override
  protected String dbUser(final Connection connection) {
    return null;
  }

  @Override
  protected String dbName(final Connection connection) {
    return null;
  }

  @Override
  protected String dbConnectionString(final Connection connection) {
    return connection.getHost() + ":" + connection.getPort();
  }
}
