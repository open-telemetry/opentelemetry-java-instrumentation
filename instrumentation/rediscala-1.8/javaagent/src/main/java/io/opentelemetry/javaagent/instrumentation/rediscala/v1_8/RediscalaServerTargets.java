/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.ACTOR_REQUEST_TARGET;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.CLUSTER_TARGET;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.POOL_REQUEST_TARGET;
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
import redis.RedisClientPoolLike;
import redis.RedisServer;
import redis.RoundRobinPoolRequest;
import redis.SentinelMonitoredRedisBlockingClient;
import redis.SentinelMonitoredRedisClient;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.collection.Iterator;
import scala.collection.mutable.HashMap;

public class RediscalaServerTargets {

  private static final Logger logger = Logger.getLogger(RediscalaServerTargets.class.getName());

  private static final String CLUSTER_CLASS_NAME = "redis.RedisCluster";
  private static final String MUTABLE_POOL_CLASS_NAME = "redis.RedisClientMutablePool";
  private static final String SENTINEL_MASTER_SLAVES_CLASS_NAME =
      "redis.SentinelMonitoredRedisClientMasterSlaves";

  @Nullable private static final Class<?> CLUSTER_CLASS = findClass(CLUSTER_CLASS_NAME);

  @Nullable
  private static final Method CLUSTER_REDIS_SERVERS = findMethod(CLUSTER_CLASS, "redisServers");

  // Scala collection return types differ between the Scala 2.12 and 2.13 builds, so
  // collection-returning methods are resolved reflectively rather than called directly.
  @Nullable
  private static final Method POOL_REDIS_SERVERS =
      findMethod(RedisClientPool.class, "redisServers");

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

  public static final VirtualField<HashMap<?, ?>, MutablePoolState> MUTABLE_POOL_STATE =
      VirtualField.find(HashMap.class, MutablePoolState.class);

  @Nullable
  static final Class<?> SENTINEL_MASTER_SLAVES_CLASS = findClass(SENTINEL_MASTER_SLAVES_CLASS_NAME);

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

  @Nullable
  static Class<?> findClass(String className) {
    try {
      return Class.forName(className, false, RediscalaServerTargets.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  static Method findMethod(@Nullable Class<?> declaringClass, String methodName) {
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
  public static RedisServerTarget get(@Nullable Object client) {
    if (MUTABLE_POOL_CLASS != null && MUTABLE_POOL_CLASS.isInstance(client)) {
      return ofMutablePool(client);
    }
    if (client instanceof RedisClientActorLike) {
      return of(client);
    }
    if (client instanceof ActorRequest) {
      return RediscalaSingletons.getServerTarget(ACTOR_REQUEST_TARGET, (ActorRequest) client);
    }
    if (client instanceof RoundRobinPoolRequest) {
      return RediscalaSingletons.getServerTarget(
          POOL_REQUEST_TARGET, (RoundRobinPoolRequest) client);
    }
    if (CLUSTER_CLASS != null && CLUSTER_CLASS.isInstance(client)) {
      return RediscalaSingletons.getServerTarget(CLUSTER_TARGET, (RedisClientPoolLike) client);
    }
    return of(client);
  }

  @Nullable
  static RedisServerTarget of(@Nullable Object client) {
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
    if (CLUSTER_CLASS != null && CLUSTER_CLASS.isInstance(client)) {
      return ofPool(client, CLUSTER_REDIS_SERVERS);
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
      if (!(slave instanceof RedisServer)) {
        return null;
      }
      RedisServer redisServer = (RedisServer) slave;
      slaveEndpoints.add(RedisServerTarget.endpoint(redisServer.host(), redisServer.port()));
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
      if (!(sentinel instanceof Tuple2)) {
        endpoints.add(null);
        continue;
      }
      Tuple2<?, ?> endpoint = (Tuple2<?, ?>) sentinel;
      if (!(endpoint._1() instanceof String) || !(endpoint._2() instanceof Number)) {
        endpoints.add(null);
        continue;
      }
      endpoints.add(
          RedisServerTarget.endpoint((String) endpoint._1(), ((Number) endpoint._2()).intValue()));
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
      if (!(server instanceof RedisServer)) {
        endpoints.add(null);
        continue;
      }
      RedisServer redisServer = (RedisServer) server;
      endpoints.add(RedisServerTarget.endpoint(redisServer.host(), redisServer.port()));
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
    if (!(connections instanceof HashMap)) {
      return null;
    }

    MutablePoolState state = MUTABLE_POOL_STATE.get((HashMap<?, ?>) connections);
    if (state == null) {
      return null;
    }
    return state.target();
  }

  public static void initializeMutablePool(Object pool) {
    if (MUTABLE_POOL_CONNECTIONS == null) {
      return;
    }

    Object connections;
    try {
      connections = MUTABLE_POOL_CONNECTIONS.invoke(pool);
    } catch (ReflectiveOperationException | RuntimeException e) {
      logger.log(FINE, "Failed to initialize rediscala mutable pool server state", e);
      return;
    }
    if (!(connections instanceof HashMap)) {
      return;
    }

    HashMap<?, ?> map = (HashMap<?, ?>) connections;
    MutablePoolState state = MutablePoolState.fromMap(map);
    MUTABLE_POOL_STATE.set(map, state);
  }

  @Nullable
  public static MutablePoolState getMutablePoolState(HashMap<?, ?> map) {
    return MUTABLE_POOL_STATE.get(map);
  }

  @Nullable
  public static String endpoint(@Nullable Object server) {
    if (!(server instanceof RedisServer)) {
      return null;
    }
    RedisServer redisServer = (RedisServer) server;
    return RedisServerTarget.endpoint(redisServer.host(), redisServer.port());
  }

  public static final class MutablePoolState {
    // Supported pool writers hold the library-owned carrier or pool monitor through the map commit.
    @Nullable private volatile RedisServerTarget target;

    private MutablePoolState() {}

    static MutablePoolState fromMap(HashMap<?, ?> map) {
      MutablePoolState state = new MutablePoolState();
      state.refresh(map);
      return state;
    }

    @Nullable
    RedisServerTarget target() {
      return target;
    }

    public void markUnavailable() {
      target = null;
    }

    public void refresh(HashMap<?, ?> map) {
      try {
        target = snapshot(map);
      } catch (RuntimeException e) {
        markUnavailable();
        logger.log(FINE, "Failed to snapshot rediscala mutable pool servers", e);
      }
    }

    @Nullable
    private static RedisServerTarget snapshot(HashMap<?, ?> map) {
      List<String> endpoints = new ArrayList<>();
      Iterator<?> iterator = map.iterator();
      while (iterator.hasNext()) {
        Object entry = iterator.next();
        if (!(entry instanceof Tuple2)) {
          return null;
        }
        String endpoint = endpoint(((Tuple2<?, ?>) entry)._1());
        if (endpoint == null) {
          return null;
        }
        endpoints.add(endpoint);
      }
      return RedisServerTarget.ofUnorderedEndpoints(endpoints);
    }
  }

  private RediscalaServerTargets() {}
}
