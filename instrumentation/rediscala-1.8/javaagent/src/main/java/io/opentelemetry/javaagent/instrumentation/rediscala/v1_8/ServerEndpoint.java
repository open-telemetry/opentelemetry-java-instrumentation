/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import javax.annotation.Nullable;
import redis.RedisClientActorLike;
import scala.Option;

public class ServerEndpoint {
  private final String host;
  private final int port;
  @Nullable private final Integer databaseIndex;

  private ServerEndpoint(String host, int port, @Nullable Integer databaseIndex) {
    this.host = host;
    this.port = port;
    this.databaseIndex = databaseIndex;
  }

  public static ServerEndpoint create(RedisClientActorLike client) {
    return new ServerEndpoint(client.host(), client.port(), databaseIndex(client));
  }

  String getHost() {
    return host;
  }

  int getPort() {
    return port;
  }

  @Nullable
  Integer getDatabaseIndex() {
    return databaseIndex;
  }

  @Nullable
  private static Integer databaseIndex(RedisClientActorLike client) {
    // db is a scala Option[Int], which erases to an Option holding a boxed Integer. It is empty
    // when the client was not configured with an index, in which case the server default database
    // is used.
    Option<Object> db = client.db();
    return db.isDefined() ? (Integer) db.get() : null;
  }
}
