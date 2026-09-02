/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.logging.Level.FINE;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import redis.RedisClientActorLike;
import redis.RedisClientMasterSlaves;
import redis.SentinelMonitoredRedisClient;
import scala.Option;

public class ServerEndpoint {
  private static final Logger logger = Logger.getLogger(ServerEndpoint.class.getName());

  private static final String SENTINEL_MASTER_SLAVES_CLASS_NAME =
      "redis.SentinelMonitoredRedisClientMasterSlaves";

  @Nullable
  private static final Class<?> SENTINEL_MASTER_SLAVES_CLASS =
      findClass(SENTINEL_MASTER_SLAVES_CLASS_NAME);

  @Nullable
  private static final Method SENTINEL_MASTER_SLAVES_MASTER_CLIENT =
      findMethod(SENTINEL_MASTER_SLAVES_CLASS, "masterClient");

  private final String host;
  private final int port;
  private final int databaseIndex;

  public static ServerEndpoint create(RedisClientActorLike client) {
    return new ServerEndpoint(client.host(), client.port(), databaseIndex(client));
  }

  @Nullable
  public static ServerEndpoint create(Object client) {
    return create(client, true);
  }

  @Nullable
  public static ServerEndpoint create(Object client, boolean useMasterClient) {
    if (client instanceof RedisClientActorLike) {
      return create((RedisClientActorLike) client);
    }
    if (client instanceof SentinelMonitoredRedisClient) {
      RedisClientActorLike redisClient = ((SentinelMonitoredRedisClient) client).redisClient();
      return redisClient != null ? create(redisClient) : null;
    }
    if (useMasterClient && client instanceof RedisClientMasterSlaves) {
      return create(((RedisClientMasterSlaves) client).masterClient());
    }
    if (useMasterClient
        && SENTINEL_MASTER_SLAVES_CLASS != null
        && SENTINEL_MASTER_SLAVES_CLASS.isInstance(client)) {
      return createMasterClient(client);
    }
    return null;
  }

  private ServerEndpoint(String host, int port, int databaseIndex) {
    this.host = host;
    this.port = port;
    this.databaseIndex = databaseIndex;
  }

  @Nullable
  private static ServerEndpoint createMasterClient(Object client) {
    if (SENTINEL_MASTER_SLAVES_MASTER_CLIENT == null) {
      return null;
    }
    Object masterClient;
    try {
      masterClient = SENTINEL_MASTER_SLAVES_MASTER_CLIENT.invoke(client);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the rediscala Sentinel master client", e);
      return null;
    }
    return masterClient instanceof RedisClientActorLike
        ? create((RedisClientActorLike) masterClient)
        : null;
  }

  @Nullable
  private static Class<?> findClass(String className) {
    try {
      return Class.forName(className, false, ServerEndpoint.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findMethod(@Nullable Class<?> declaringClass, String methodName) {
    if (declaringClass == null) {
      return null;
    }
    try {
      return declaringClass.getMethod(methodName);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
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
