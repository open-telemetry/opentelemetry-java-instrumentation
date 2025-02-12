/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRedissonClientTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractRedissonClientTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static String ip;

  private static int port;
  private static String address;
  private RedissonClient redisson;

  @BeforeAll
  static void setupAll() throws UnknownHostException {
    redisServer.start();
    ip = InetAddress.getByName(redisServer.getHost()).getHostAddress();
    port = redisServer.getMappedPort(6379);
    address = redisServer.getHost() + ":" + port;
  }

  @AfterAll
  static void cleanupAll() {
    redisServer.stop();
  }

  @BeforeEach
  void setup() throws InvocationTargetException, IllegalAccessException {
    String newAddress = address;
    if (useRedisProtocol()) {
      // Newer versions of redisson require scheme, older versions forbid it
      newAddress = "redis://" + address;
    }
    Config config = new Config();
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(newAddress);
    singleServerConfig.setTimeout(30_000);
    try {
      // disable connection ping if it exists
      singleServerConfig
          .getClass()
          .getMethod("setPingConnectionInterval", int.class)
          .invoke(singleServerConfig, 0);
    } catch (NoSuchMethodException ignored) {
      // ignored
    }
    redisson = Redisson.create(config);
    testing.clearData();
  }

  @Test
  void stringCommand() {
    RBucket<String> keyObject = redisson.getBucket("foo");
    keyObject.set("bar");
    keyObject.get();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("SET", "GET"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "GET foo"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void batchCommand()
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    RBatch batch = createBatch(redisson);
    assertThat(batch).isNotNull();
    batch.getBucket("batch1").setAsync("v1");
    batch.getBucket("batch2").setAsync("v2");
    // Adapt different method signature:
    // `BatchResult<?> execute()` and `List<?> execute()`
    invokeExecute(batch);
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("SET", "GET"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DB Query")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET batch1 ?;SET batch2 ?"))));
  }

  private static void invokeExecute(RBatch batch)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    batch.getClass().getMethod("execute").invoke(batch);
  }

  @Test
  void atomicBatchCommand() {
    try {
      // available since 3.7.2
      Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
    } catch (ClassNotFoundException exception) {
      Assume.assumeNoException(exception);
    }

    testing.runWithSpan(
        "parent",
        () -> {
          BatchOptions batchOptions =
              BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC);
          RBatch batch = redisson.createBatch(batchOptions);
          batch.getBucket("batch1").setAsync("v1");
          batch.getBucket("batch2").setAsync("v2");
          batch.execute();
        });
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("DB Query", "SET", "EXEC"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasKind(INTERNAL),
                span ->
                    span.hasName("DB Query")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "MULTI;SET batch1 ?"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET batch2 ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("EXEC")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "EXEC"),
                            equalTo(maybeStable(DB_OPERATION), "EXEC"))
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void listCommand() {
    RList<String> strings = redisson.getList("list1");
    strings.add("a");

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RPUSH")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "RPUSH list1 ?"),
                            equalTo(maybeStable(DB_OPERATION), "RPUSH"))
                        .hasNoParent()));
  }

  @Test
  void hashCommand() {
    RMap<String, String> map = redisson.getMap("map1");
    map.put("key1", "value1");
    map.get("key1");

    String script =
        "local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v";
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EVAL")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                String.format("EVAL %s 1 map1 ? ?", script)),
                            equalTo(maybeStable(DB_OPERATION), "EVAL"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HGET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "HGET map1 key1"),
                            equalTo(maybeStable(DB_OPERATION), "HGET"))));
  }

  @Test
  void setCommand() {
    RSet<String> set = redisson.getSet("set1");
    set.add("s1");

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SADD")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SADD set1 ?"),
                            equalTo(maybeStable(DB_OPERATION), "SADD"))));
  }

  @Test
  void sortedSetCommand()
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Map<String, Double> scores = new HashMap<>();
    scores.put("u1", 1.0d);
    scores.put("u2", 3.0d);
    scores.put("u3", 0.0d);
    RScoredSortedSet<String> sortSet = redisson.getScoredSortedSet("sort_set1");
    //  Adapt different method signature:
    // `Long addAll(Map<V, Double> objects);` and `int addAll(Map<V, Double> objects);`
    invokeAddAll(sortSet, scores);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("ZADD"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ZADD")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "ZADD sort_set1 ? ? ? ? ? ?"),
                            equalTo(maybeStable(DB_OPERATION), "ZADD"))));
  }

  private static void invokeAddAll(RScoredSortedSet<String> object, Map<String, Double> arg)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    object.getClass().getMethod("addAll", Map.class).invoke(object, arg);
  }

  @Test
  void atomicLongCommand() {
    RAtomicLong atomicLong = redisson.getAtomicLong("AtomicLong");
    atomicLong.incrementAndGet();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INCR")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "INCR AtomicLong"),
                            equalTo(maybeStable(DB_OPERATION), "INCR"))));
  }

  @Test
  void lockCommand() {
    RLock lock = redisson.getLock("lock");
    lock.lock();
    try {
      logger.info("enter lock block");
    } finally {
      lock.unlock();
    }

    List<Consumer<TraceAssert>> traceAsserts = new ArrayList<>();
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EVAL")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_OPERATION), "EVAL"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                stringAssert -> stringAssert.startsWith("EVAL")))));
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EVAL")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, (long) port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_OPERATION), "EVAL"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                stringAssert -> stringAssert.startsWith("EVAL")))));
    if (lockHas3Traces()) {
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("DEL")
                          .hasKind(CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, "ipv4"),
                              equalTo(NETWORK_PEER_ADDRESS, ip),
                              equalTo(NETWORK_PEER_PORT, (long) port),
                              equalTo(maybeStable(DB_SYSTEM), "redis"),
                              equalTo(maybeStable(DB_OPERATION), "DEL"),
                              satisfies(
                                  maybeStable(DB_STATEMENT),
                                  stringAssert -> stringAssert.startsWith("DEL")))));
    }

    testing.waitAndAssertSortedTraces(orderByRootSpanKind(SpanKind.CLIENT), traceAsserts);
  }

  protected boolean useRedisProtocol() {
    return Boolean.getBoolean("testLatestDeps");
  }

  protected boolean lockHas3Traces() {
    return false;
  }

  protected RBatch createBatch(RedissonClient redisson) {
    return redisson.createBatch(BatchOptions.defaults());
  }
}
