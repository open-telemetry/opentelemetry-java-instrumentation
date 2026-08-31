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
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import redis.ActorRequest;
import redis.RedisClientActorLike;
import redis.RedisClientMasterSlaves;
import redis.RedisClientPool;
import redis.RedisServer;
import redis.RoundRobinPoolRequest;
import redis.SentinelMonitoredRedisBlockingClient;
import redis.SentinelMonitoredRedisClient;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.collection.Iterator;

public final class RediscalaServerTargets {

  private static final Logger logger = Logger.getLogger(RediscalaServerTargets.class.getName());

  private static final String MUTABLE_POOL_CLASS_NAME = "redis.RedisClientMutablePool";
  private static final String SENTINEL_MASTER_SLAVES_CLASS_NAME =
      "redis.SentinelMonitoredRedisClientMasterSlaves";

  // Scala collection return types differ between the Scala 2.12 and 2.13 builds, so
  // collection-returning methods are resolved reflectively rather than called directly.
  @Nullable
  private static final Method POOL_REDIS_SERVERS = findRedisServers(RedisClientPool.class);

  // Some rediscala forks drop the master and slaves accessors from RedisClientMasterSlaves, so both
  // are resolved reflectively and the client is skipped when either one is missing.
  @Nullable
  private static final Method MASTER_SLAVES_MASTER =
      findMethod(RedisClientMasterSlaves.class, "master");

  @Nullable
  private static final Method MASTER_SLAVES_SLAVES =
      findMethod(RedisClientMasterSlaves.class, "slaves");

  @Nullable private static final Class<?> MUTABLE_POOL_CLASS = findClass(MUTABLE_POOL_CLASS_NAME);

  @Nullable
  private static final Method MUTABLE_POOL_CONNECTIONS =
      findMethod(MUTABLE_POOL_CLASS, "redisServerConnections");

  @Nullable
  private static final Class<?> SENTINEL_MASTER_SLAVES_CLASS =
      findClass(SENTINEL_MASTER_SLAVES_CLASS_NAME);

  @Nullable
  private static final Method SENTINEL_MASTER_SLAVES_SENTINELS =
      findMethod(SENTINEL_MASTER_SLAVES_CLASS, "sentinels");

  @Nullable
  private static final Method SENTINEL_MASTER_SLAVES_MASTER =
      findMethod(SENTINEL_MASTER_SLAVES_CLASS, "master");

  @Nullable
  private static final Method SENTINELS =
      findMethod(SentinelMonitoredRedisClient.class, "sentinels");

  @Nullable
  private static final Method BLOCKING_SENTINELS =
      findMethod(SentinelMonitoredRedisBlockingClient.class, "sentinels");

  private static final VirtualField<ActorRequest, RedisServerTarget> ACTOR_REQUEST_TARGET =
      VirtualField.find(ActorRequest.class, RedisServerTarget.class);

  private static final VirtualField<RoundRobinPoolRequest, RedisServerTarget> POOL_REQUEST_TARGET =
      VirtualField.find(RoundRobinPoolRequest.class, RedisServerTarget.class);

  @Nullable
  private static Method findRedisServers(Class<?> poolClass) {
    try {
      return poolClass.getMethod("redisServers");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> findClass(String className) {
    try {
      return Class.forName(className, false, RediscalaServerTargets.class.getClassLoader());
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

  @Nullable
  public static RedisServerTarget get(Object client) {
    if (MUTABLE_POOL_CLASS != null && MUTABLE_POOL_CLASS.isInstance(client)) {
      return ofMutablePool(client);
    }
    if (client instanceof RedisClientActorLike) {
      return of(client);
    }
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
    if (SENTINEL_MASTER_SLAVES_CLASS != null && SENTINEL_MASTER_SLAVES_CLASS.isInstance(client)) {
      return ofSentinelMasterSlaves(client);
    }
    if (client instanceof RedisClientMasterSlaves) {
      return ofMasterSlaves((RedisClientMasterSlaves) client);
    }
    if (client instanceof RedisClientPool) {
      return ofPool(client, POOL_REDIS_SERVERS);
    }
    if (client instanceof RedisClientActorLike) {
      RedisClientActorLike actorClient = (RedisClientActorLike) client;
      return RedisServerTarget.ofHostAndPort(actorClient.host(), actorClient.port());
    }
    return null;
  }

  @Nullable
  private static RedisServerTarget ofSentinelMasterSlaves(Object client) {
    if (SENTINEL_MASTER_SLAVES_MASTER == null) {
      return null;
    }
    Object master;
    try {
      master = SENTINEL_MASTER_SLAVES_MASTER.invoke(client);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured rediscala Sentinel master", e);
      return null;
    }
    if (!(master instanceof String)) {
      return null;
    }
    return ofSentinel(client, SENTINEL_MASTER_SLAVES_SENTINELS, (String) master);
  }

  @Nullable
  private static RedisServerTarget ofMasterSlaves(RedisClientMasterSlaves client) {
    if (MASTER_SLAVES_MASTER == null || MASTER_SLAVES_SLAVES == null) {
      return null;
    }
    Object master;
    Object slaves;
    try {
      master = MASTER_SLAVES_MASTER.invoke(client);
      slaves = MASTER_SLAVES_SLAVES.invoke(client);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured rediscala master-slaves servers", e);
      return null;
    }
    if (!(master instanceof RedisServer) || !(slaves instanceof Iterable)) {
      return null;
    }
    List<String> slaveEndpoints = new ArrayList<>();
    Iterator<?> iterator = ((Iterable<?>) slaves).iterator();
    while (iterator.hasNext()) {
      Object slave = iterator.next();
      if (slave instanceof RedisServer) {
        RedisServer redisServer = (RedisServer) slave;
        slaveEndpoints.add(RedisServerTarget.endpoint(redisServer.host(), redisServer.port()));
      }
    }
    // the master always leads, the replicas behind it carry no meaningful order
    Collections.sort(slaveEndpoints);
    RedisServer masterServer = (RedisServer) master;
    List<String> endpoints = new ArrayList<>();
    endpoints.add(RedisServerTarget.endpoint(masterServer.host(), masterServer.port()));
    endpoints.addAll(slaveEndpoints);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofSentinel(
      Object client, @Nullable Method sentinelsMethod, String master) {
    if (sentinelsMethod == null) {
      return null;
    }
    Object sentinels;
    try {
      sentinels = sentinelsMethod.invoke(client);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured rediscala Sentinel servers", e);
      return null;
    }
    return ofSentinelEndpoints(sentinels, master);
  }

  @Nullable
  private static RedisServerTarget ofSentinelEndpoints(Object sentinels, String master) {
    if (!(sentinels instanceof Iterable)) {
      return null;
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
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(endpoints, master);
  }

  @Nullable
  private static RedisServerTarget ofPool(Object pool, @Nullable Method redisServersMethod) {
    if (redisServersMethod == null) {
      return null;
    }
    Object servers;
    try {
      servers = redisServersMethod.invoke(pool);
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
    return RedisServerTarget.ofUnorderedEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofMutablePool(Object pool) {
    if (MUTABLE_POOL_CONNECTIONS == null) {
      return null;
    }
    Object connections;
    try {
      connections = MUTABLE_POOL_CONNECTIONS.invoke(pool);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured rediscala mutable pool servers", e);
      return null;
    }
    if (!(connections instanceof Iterable)) {
      return null;
    }
    List<String> endpoints = new ArrayList<>();
    synchronized (connections) {
      Iterator<?> iterator = ((Iterable<?>) connections).iterator();
      while (iterator.hasNext()) {
        Object entry = iterator.next();
        if (entry instanceof Tuple2) {
          Object server = ((Tuple2<?, ?>) entry)._1();
          if (server instanceof RedisServer) {
            RedisServer redisServer = (RedisServer) server;
            endpoints.add(RedisServerTarget.endpoint(redisServer.host(), redisServer.port()));
          }
        }
      }
    }
    return RedisServerTarget.ofUnorderedEndpoints(endpoints);
  }

  private RediscalaServerTargets() {}
}
