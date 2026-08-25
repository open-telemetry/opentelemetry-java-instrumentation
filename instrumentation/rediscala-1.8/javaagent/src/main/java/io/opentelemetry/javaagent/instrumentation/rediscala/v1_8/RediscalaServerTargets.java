/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import redis.RedisClientActorLike;
import redis.RedisClientPool;
import redis.RedisServer;
import redis.SentinelMonitoredRedisBlockingClient;
import redis.SentinelMonitoredRedisClient;
import scala.collection.Iterable;
import scala.collection.Iterator;

/**
 * Renders the target a rediscala client was configured with.
 *
 * <p>A sentinel monitored client is named by the master it was configured to follow, a {@link
 * RedisClientPool} by the servers it was configured with, and a plain client by its own host and
 * port. None of these carry an actor system type, so this works the same for the Akka and the Pekko
 * builds of rediscala.
 */
final class RediscalaServerTargets {

  private static final Logger logger = Logger.getLogger(RediscalaServerTargets.class.getName());

  // the collection type of RedisClientPool#redisServers() differs between the scala 2.12 and the
  // scala 2.13 builds, so the method is resolved reflectively rather than called directly
  @Nullable private static final Method REDIS_SERVERS = findRedisServers();

  @Nullable
  private static Method findRedisServers() {
    try {
      return RedisClientPool.class.getMethod("redisServers");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Nullable
  static RedisServerTarget of(Object client) {
    if (client instanceof SentinelMonitoredRedisClient) {
      return RedisServerTarget.ofLogicalName(((SentinelMonitoredRedisClient) client).master());
    }
    if (client instanceof SentinelMonitoredRedisBlockingClient) {
      return RedisServerTarget.ofLogicalName(
          ((SentinelMonitoredRedisBlockingClient) client).master());
    }
    if (client instanceof RedisClientPool) {
      return ofPool((RedisClientPool) client);
    }
    if (client instanceof RedisClientActorLike) {
      RedisClientActorLike actorClient = (RedisClientActorLike) client;
      return RedisServerTarget.ofHostAndPort(actorClient.host(), actorClient.port());
    }
    return null;
  }

  @Nullable
  private static RedisServerTarget ofPool(RedisClientPool pool) {
    if (REDIS_SERVERS == null) {
      return null;
    }
    Object servers;
    try {
      servers = REDIS_SERVERS.invoke(pool);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured rediscala pool servers", e);
      return null;
    }
    if (!(servers instanceof Iterable)) {
      return null;
    }
    List<String> endpoints = new ArrayList<>();
    Iterator<?> iterator = ((Iterable<?>) servers).iterator();
    while (iterator.hasNext()) {
      Object server = iterator.next();
      if (server instanceof RedisServer) {
        RedisServer redisServer = (RedisServer) server;
        endpoints.add(RedisServerTarget.endpoint(redisServer.host(), redisServer.port()));
      }
    }
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  private RediscalaServerTargets() {}
}
