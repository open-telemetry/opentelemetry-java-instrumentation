/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.ACTOR_REQUEST_TARGET;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.CLUSTER_TARGET;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.POOL_REQUEST_TARGET;
import static java.util.Collections.emptyMap;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

  public static final VirtualField<scala.collection.mutable.HashMap<?, ?>, MutablePoolState>
      MUTABLE_POOL_STATE =
          VirtualField.find(scala.collection.mutable.HashMap.class, MutablePoolState.class);

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
    if (!(connections instanceof scala.collection.mutable.HashMap)) {
      return null;
    }

    MutablePoolState state =
        MUTABLE_POOL_STATE.get((scala.collection.mutable.HashMap<?, ?>) connections);
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
    if (!(connections instanceof scala.collection.mutable.HashMap)) {
      return;
    }

    scala.collection.mutable.HashMap<?, ?> map =
        (scala.collection.mutable.HashMap<?, ?>) connections;
    MutablePoolState state;
    try {
      state = MutablePoolState.fromMap(map);
    } catch (RuntimeException e) {
      logger.log(FINE, "Failed to snapshot rediscala mutable pool servers", e);
      state = MutablePoolState.unavailable();
    }
    MUTABLE_POOL_STATE.set(map, state);
  }

  @Nullable
  public static MutablePoolState getMutablePoolState(scala.collection.mutable.HashMap<?, ?> map) {
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

  // Scala's generic Map API is erased at the Java boundary, so membership is checked by the
  // library implementation using the exact key object.
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static boolean contains(scala.collection.mutable.HashMap<?, ?> map, @Nullable Object key) {
    return ((scala.collection.Map) map).contains(key);
  }

  public static final class MutablePoolState {
    private static final Snapshot UNAVAILABLE = new Snapshot(false, emptyMap(), null);

    private final AtomicReference<Snapshot> snapshot;

    private MutablePoolState(Snapshot snapshot) {
      this.snapshot = new AtomicReference<>(snapshot);
    }

    static MutablePoolState fromMap(scala.collection.mutable.HashMap<?, ?> map) {
      List<String> endpoints = new ArrayList<>();
      Iterator<?> iterator = map.iterator();
      while (iterator.hasNext()) {
        Object entry = iterator.next();
        if (!(entry instanceof Tuple2)) {
          return unavailable();
        }
        String endpoint = endpoint(((Tuple2<?, ?>) entry)._1());
        if (endpoint == null) {
          return unavailable();
        }
        endpoints.add(endpoint);
      }
      return new MutablePoolState(Snapshot.available(endpoints));
    }

    private static MutablePoolState unavailable() {
      return new MutablePoolState(UNAVAILABLE);
    }

    @Nullable
    RedisServerTarget target() {
      Snapshot current = snapshot.get();
      return current.available ? current.target : null;
    }

    public void add(String endpoint) {
      update(endpoint, 1);
    }

    public void remove(String endpoint) {
      update(endpoint, -1);
    }

    public void markUnavailable() {
      snapshot.set(UNAVAILABLE);
    }

    public boolean isAvailable() {
      return snapshot.get().available;
    }

    private void update(String endpoint, int delta) {
      if (endpoint == null) {
        markUnavailable();
        return;
      }

      while (true) {
        Snapshot current = snapshot.get();
        if (!current.available) {
          return;
        }

        Map<String, Integer> counts = new HashMap<>(current.counts);
        int count = counts.getOrDefault(endpoint, 0);
        int updatedCount = count + delta;
        if (updatedCount < 0) {
          markUnavailable();
          return;
        }
        if (updatedCount == 0) {
          counts.remove(endpoint);
        } else {
          counts.put(endpoint, updatedCount);
        }

        Snapshot updated;
        try {
          updated = Snapshot.available(counts);
        } catch (RuntimeException e) {
          markUnavailable();
          return;
        }
        if (snapshot.compareAndSet(current, updated)) {
          return;
        }
      }
    }

    private static final class Snapshot {
      private final boolean available;
      private final Map<String, Integer> counts;
      @Nullable private final RedisServerTarget target;

      private Snapshot(
          boolean available, Map<String, Integer> counts, @Nullable RedisServerTarget target) {
        this.available = available;
        this.counts = counts;
        this.target = target;
      }

      private static Snapshot available(List<String> endpoints) {
        Map<String, Integer> counts = new HashMap<>();
        for (String endpoint : endpoints) {
          counts.put(endpoint, counts.getOrDefault(endpoint, 0) + 1);
        }
        return available(counts);
      }

      private static Snapshot available(Map<String, Integer> counts) {
        List<String> endpoints = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
          for (int i = 0; i < entry.getValue(); i++) {
            endpoints.add(entry.getKey());
          }
        }
        RedisServerTarget target = RedisServerTarget.ofUnorderedEndpoints(endpoints);
        return new Snapshot(true, Collections.unmodifiableMap(new HashMap<>(counts)), target);
      }
    }
  }

  private RediscalaServerTargets() {}
}
