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

package io.opentelemetry.auto.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.opentelemetry.instrumentation.api.decorator.DatabaseClientTracer;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public abstract class LettuceAbstractDatabaseClientTracer<QUERY>
    extends DatabaseClientTracer<RedisURI, QUERY> {
  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.lettuce-5.0";
  }

  @Override
  protected String dbSystem(final RedisURI connection) {
    return DbSystem.REDIS;
  }

  @Override
  protected String dbUser(final RedisURI connection) {
    return null;
  }

  @Override
  protected String dbName(final RedisURI connection) {
    return null;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisURI redisURI) {
    return new InetSocketAddress(redisURI.getHost(), redisURI.getPort());
  }

  @Override
  public Span onConnection(final Span span, final RedisURI connection) {
    span.setAttribute("db.redis.dbIndex", connection.getDatabase());
    return super.onConnection(span, connection);
  }
}
