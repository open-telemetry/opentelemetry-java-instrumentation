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

package io.opentelemetry.instrumentation.auto.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public abstract class LettuceAbstractDatabaseClientTracer<QUERY>
    extends DatabaseClientTracer<RedisURI, QUERY> {

  @Override
  protected String dbSystem(RedisURI connection) {
    return DbSystem.REDIS;
  }

  @Override
  protected String dbUser(RedisURI connection) {
    return null;
  }

  @Override
  protected String dbName(RedisURI connection) {
    return null;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisURI redisURI) {
    return null;
  }

  @Override
  public Span onConnection(Span span, RedisURI connection) {
    if (connection != null) {
      NetPeerUtils.setNetPeer(span, connection.getHost(), connection.getPort());
      span.setAttribute("db.redis.dbIndex", connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.lettuce-4.0";
  }
}
