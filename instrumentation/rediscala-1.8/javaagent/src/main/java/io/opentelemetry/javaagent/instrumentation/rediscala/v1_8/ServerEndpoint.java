/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import javax.annotation.Nullable;
import redis.RedisClientActorLike;
import redis.SentinelMonitoredRedisClient;
import scala.Option;

public class ServerEndpoint {
  private final String host;
  private final int port;
  private final int databaseIndex;

  public static ServerEndpoint create(RedisClientActorLike client) {
    return new ServerEndpoint(client.host(), client.port(), databaseIndex(client));
  }

  @Nullable
  public static ServerEndpoint create(Object client) {
    if (client instanceof RedisClientActorLike) {
      return create((RedisClientActorLike) client);
    }
    if (client instanceof SentinelMonitoredRedisClient) {
      RedisClientActorLike redisClient = ((SentinelMonitoredRedisClient) client).redisClient();
      return redisClient != null ? create(redisClient) : null;
    }
    return null;
  }

  private ServerEndpoint(String host, int port, int databaseIndex) {
    this.host = host;
    this.port = port;
    this.databaseIndex = databaseIndex;
  }

  String getHost() {
    return host;
  }

  int getPort() {
    return port;
  }

  int getDatabaseIndex() {
    return databaseIndex;
  }

  private static int databaseIndex(RedisClientActorLike client) {
    // db is a scala Option[Int], which erases to an Option holding a boxed Integer. When it is
    // empty rediscala sends no SELECT and the connection stays on the server default database, so
    // the index is 0.
    Option<Object> db = client.db();
    return db.isDefined() ? (Integer) db.get() : 0;
  }
}
