/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import redis.ActorRequest;
import redis.RedisClientActorLike;
import redis.RedisClientPool;
import redis.RedisServer;
import redis.RoundRobinPoolRequest;
import redis.SentinelMonitoredRedisBlockingClient;
import redis.SentinelMonitoredRedisClient;
import scala.Tuple2;
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

  // Scala collection return types differ between the Scala 2.12 and 2.13 builds, so
  // collection-returning methods are resolved reflectively rather than called directly.
  @Nullable private static final Method REDIS_SERVERS = findRedisServers();

  @Nullable
  private static final Method SENTINELS = findSentinels(SentinelMonitoredRedisClient.class);

  @Nullable
  private static final Method BLOCKING_SENTINELS =
      findSentinels(SentinelMonitoredRedisBlockingClient.class);

  private static final VirtualField<ActorRequest, RedisServerTarget> ACTOR_REQUEST_TARGET =
      VirtualField.find(ActorRequest.class, RedisServerTarget.class);

  private static final VirtualField<RoundRobinPoolRequest, RedisServerTarget> POOL_REQUEST_TARGET =
      VirtualField.find(RoundRobinPoolRequest.class, RedisServerTarget.class);

  @Nullable
  private static Method findRedisServers() {
    try {
      return RedisClientPool.class.getMethod("redisServers");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Nullable
  private static Method findSentinels(Class<?> clientClass) {
    try {
      return clientClass.getMethod("sentinels");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Nullable
  static RedisServerTarget get(Object client) {
    if (client instanceof ActorRequest) {
      return get(ACTOR_REQUEST_TARGET, (ActorRequest) client);
    }
    if (client instanceof RoundRobinPoolRequest) {
      return get(POOL_REQUEST_TARGET, (RoundRobinPoolRequest) client);
    }
    return of(client);
  }

  @Nullable
  private static <T> RedisServerTarget get(
      VirtualField<T, RedisServerTarget> targetField, T client) {
    RedisServerTarget target = targetField.get(client);
    if (target == null) {
      target = of(client);
      if (target != null) {
        targetField.set(client, target);
      }
    }
    return target;
  }

  @Nullable
  private static RedisServerTarget of(Object client) {
    if (client instanceof SentinelMonitoredRedisClient) {
      return ofSentinel(client, SENTINELS, ((SentinelMonitoredRedisClient) client).master());
    }
    if (client instanceof SentinelMonitoredRedisBlockingClient) {
      return ofSentinel(
          client, BLOCKING_SENTINELS, ((SentinelMonitoredRedisBlockingClient) client).master());
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
  private static RedisServerTarget ofSentinel(
      Object client, @Nullable Method sentinelsMethod, String master) {
    if (sentinelsMethod == null) {
      return RedisServerTarget.ofLogicalName(master);
    }
    Object sentinels;
    try {
      sentinels = sentinelsMethod.invoke(client);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured rediscala Sentinel servers", e);
      return RedisServerTarget.ofLogicalName(master);
    }
    return ofSentinelEndpoints(sentinels, master);
  }

  @Nullable
  private static RedisServerTarget ofSentinelEndpoints(Object sentinels, String master) {
    if (!(sentinels instanceof Iterable)) {
      return RedisServerTarget.ofLogicalName(master);
    }
    List<String> endpoints = new ArrayList<>();
    Iterator<?> iterator = ((Iterable<?>) sentinels).iterator();
    while (iterator.hasNext()) {
      Object sentinel = iterator.next();
      if (sentinel instanceof Tuple2) {
        Tuple2<?, ?> endpoint = (Tuple2<?, ?>) sentinel;
        if (endpoint._1() instanceof String && endpoint._2() instanceof Number) {
          endpoints.add(
              RedisServerTarget.endpoint(
                  (String) endpoint._1(), ((Number) endpoint._2()).intValue()));
        }
      }
    }
    return RedisServerTarget.ofEndpointsAndLogicalName(endpoints, master);
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
