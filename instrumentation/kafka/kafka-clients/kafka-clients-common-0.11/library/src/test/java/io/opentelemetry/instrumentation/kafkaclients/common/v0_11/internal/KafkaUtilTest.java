/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.Metadata;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.Cluster;
import org.junit.jupiter.api.Test;

class KafkaUtilTest {

  @Test
  void clusterIdFromMetadata_null_returnsNull() {
    assertThat(KafkaUtil.clusterIdFromMetadata(null)).isNull();
  }

  @Test
  void clusterIdFromMetadata_validId_returnsId() {
    Metadata metadata = new Metadata(0, Long.MAX_VALUE, false);
    metadata.update(
        new Cluster("test-cluster", emptyList(), emptyList(), emptySet(), emptySet()),
        emptySet(),
        0);
    assertThat(KafkaUtil.clusterIdFromMetadata(metadata)).isEqualTo("test-cluster");
  }

  @Test
  void clusterIdFromMetadata_emptyId_returnsNull() {
    Metadata metadata = new Metadata(0, Long.MAX_VALUE, false);
    metadata.update(
        new Cluster("", emptyList(), emptyList(), emptySet(), emptySet()), emptySet(), 0);
    assertThat(KafkaUtil.clusterIdFromMetadata(metadata)).isNull();
  }

  @Test
  void clusterIdFromMetadata_noClusterId_returnsNull() {
    // Fresh Metadata with no update — fetch() returns Cluster.empty(), whose clusterId is null.
    Metadata metadata = new Metadata(0, Long.MAX_VALUE, false);
    assertThat(KafkaUtil.clusterIdFromMetadata(metadata)).isNull();
  }

  @Test
  void pendingClusterId_throttlesMetadataReads() {
    // Metadata.fetch() locks the instance shared with the Kafka network thread, so once the first
    // span has had its pair of reads, the spans queued behind it must be turned away.
    KafkaClusterId pending = KafkaClusterId.of(new Metadata(0, Long.MAX_VALUE, false));

    // The first span reads at its start and again at its end.
    assertThat(pending.shouldReadMetadataNow()).isTrue();
    assertThat(pending.shouldReadMetadataNow()).isTrue();
    for (int i = 0; i < 100; i++) {
      assertThat(pending.shouldReadMetadataNow()).isFalse();
    }
  }

  @Test
  void pendingClusterId_neverBecomesTerminal() throws InterruptedException {
    // A burst of concurrent sends at startup asks far more often than any fixed attempt budget
    // would allow, and each producer span asks twice (at span start and again at span end). None of
    // that may latch the entry closed, or a client whose broker is merely slow would lose the
    // attribute for its whole lifetime.
    KafkaClusterId pending = KafkaClusterId.of(new Metadata(0, Long.MAX_VALUE, false));
    for (int i = 0; i < 1000; i++) {
      pending.shouldReadMetadataNow();
    }

    Thread.sleep(NANOSECONDS.toMillis(KafkaClusterId.RETRY_INTERVAL_NANOS) + 50);

    assertThat(pending.shouldReadMetadataNow()).isTrue();
  }

  @Test
  void resolvedAndUnavailableClusterId_neverReadMetadata() {
    // Once the id is known there is nothing left to read, and UNAVAILABLE holds no Metadata at all.
    KafkaClusterId resolved = KafkaClusterId.of(new Metadata(0, Long.MAX_VALUE, false));
    resolved.resolve("test-cluster");

    assertThat(resolved.shouldReadMetadataNow()).isFalse();
    assertThat(KafkaClusterId.UNAVAILABLE.shouldReadMetadataNow()).isFalse();
  }

  @Test
  void initializeClusterId_publishesTheHolderWithoutReadingMetadata() {
    // The throttle lives in the holder, so the holder has to reach the VirtualField before anything
    // reads Metadata -- otherwise the very first reads are unguarded.
    AtomicInteger fetches = new AtomicInteger();
    Metadata metadata = countingMetadata(fetches, Cluster.empty());
    Consumer<?, ?> client = stubConsumer();
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();

    KafkaClusterId published =
        KafkaUtil.initializeClusterId(client, field, new HolderWithMetadata(metadata));

    assertThat(fetches.get()).isZero();
    assertThat(field.get(client)).isSameAs(published);
  }

  @Test
  void resolvingClusterId_leavesThePublishedHolderInPlace() {
    // VirtualField has no compare-and-set, so resolution must not write it again: a concurrent
    // pending write would otherwise clobber the id and send later spans back to reading metadata.
    AtomicInteger fetches = new AtomicInteger();
    Metadata metadata =
        countingMetadata(
            fetches, new Cluster("test-cluster", emptyList(), emptyList(), emptySet(), emptySet()));
    Consumer<?, ?> client = stubConsumer();
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();

    KafkaClusterId published =
        KafkaUtil.initializeClusterId(client, field, new HolderWithMetadata(metadata));

    assertThat(KafkaUtil.readClusterId(published)).isEqualTo("test-cluster");
    assertThat(field.get(client)).isSameAs(published);
    assertThat(published.clusterId()).isEqualTo("test-cluster");
    // The resolved id is served from the holder, without another metadata read.
    assertThat(KafkaUtil.readClusterId(published)).isEqualTo("test-cluster");
    assertThat(fetches.get()).isEqualTo(1);
  }

  @Test
  void secondReadIsAllowedImmediately_soOnEndCanSeeALateClusterId() {
    // The broker's first metadata response lands between a producer span's start and its end, which
    // is why KafkaProducerAttributesExtractor.onEnd re-reads. Throttling that second read away
    // drops the attribute from every span created before the response.
    AtomicInteger fetches = new AtomicInteger();
    AtomicReference<Cluster> reported = new AtomicReference<>(Cluster.empty());
    Metadata metadata = mock(Metadata.class);
    when(metadata.fetch())
        .thenAnswer(
            invocation -> {
              fetches.incrementAndGet();
              return reported.get();
            });
    Consumer<?, ?> client = stubConsumer();

    KafkaClusterId holder =
        KafkaUtil.initializeClusterId(client, clusterIdField(), new HolderWithMetadata(metadata));

    // Span start: the broker has not reported a cluster id yet.
    assertThat(KafkaUtil.readClusterId(holder)).isNull();

    reported.set(new Cluster("test-cluster", emptyList(), emptyList(), emptySet(), emptySet()));

    // Span end, well inside the retry interval: the read must still happen.
    assertThat(KafkaUtil.readClusterId(holder)).isEqualTo("test-cluster");
    assertThat(fetches.get()).isEqualTo(2);
  }

  @Test
  void furtherReadsWithinTheIntervalAreThrottled() {
    // The pair above is the whole allowance: a client whose broker never reports an id must not
    // enter Metadata.fetch() once per span.
    AtomicInteger fetches = new AtomicInteger();
    Metadata metadata = countingMetadata(fetches, Cluster.empty());
    Consumer<?, ?> client = stubConsumer();

    KafkaClusterId holder =
        KafkaUtil.initializeClusterId(client, clusterIdField(), new HolderWithMetadata(metadata));

    for (int i = 0; i < 20; i++) {
      assertThat(KafkaUtil.readClusterId(holder)).isNull();
    }

    assertThat(fetches.get()).isEqualTo(2);
  }

  @Test
  void concurrentReadsShareOneThrottle() throws InterruptedException {
    // Metadata.fetch() locks the instance shared with the Kafka network thread, so the throttle is
    // what a burst of sends has to contend on. It lives in the holder, so the read count is set by
    // the client, not by how many threads arrive.
    AtomicInteger fetches = new AtomicInteger();
    Metadata metadata = countingMetadata(fetches, Cluster.empty());
    Consumer<?, ?> client = stubConsumer();
    KafkaClusterId holder =
        KafkaUtil.initializeClusterId(client, clusterIdField(), new HolderWithMetadata(metadata));

    int threads = 32;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        pool.execute(
            () -> {
              try {
                start.await();
                KafkaUtil.readClusterId(holder);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertThat(done.await(30, SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    // The first span's two reads, and nothing more, for any number of threads.
    assertThat(fetches.get()).isEqualTo(2);
  }

  @Test
  void extractMetadataFromHolder_null_returnsNull() {
    assertThat(KafkaUtil.extractMetadataFromHolder(null)).isNull();
  }

  @Test
  void extractMetadataFromHolder_noMetadataField_returnsNull() {
    // String has no 'metadata' field
    assertThat(KafkaUtil.extractMetadataFromHolder("not-a-holder")).isNull();
  }

  @Test
  void extractMetadataFromHolder_withMetadataField_returnsMetadata() {
    Metadata metadata = new Metadata(0, Long.MAX_VALUE, false);
    HolderWithMetadata holder = new HolderWithMetadata(metadata);
    assertThat(KafkaUtil.extractMetadataFromHolder(holder)).isSameAs(metadata);
  }

  @Test
  void extractMetadataFromHolder_nullMetadataField_returnsNull() {
    HolderWithMetadata holder = new HolderWithMetadata(null);
    assertThat(KafkaUtil.extractMetadataFromHolder(holder)).isNull();
  }

  @Test
  void extractMetadataFromHolder_metadataOnSuperclass_returnsMetadata() {
    // SubclassHolder inherits 'metadata' from HolderWithMetadata; getDeclaredField() alone would
    // miss it — only superclass traversal finds it.
    Metadata metadata = new Metadata(0, Long.MAX_VALUE, false);
    SubclassHolder holder = new SubclassHolder(metadata);
    assertThat(KafkaUtil.extractMetadataFromHolder(holder)).isSameAs(metadata);
  }

  @Test
  void extractMetadataFromHolder_unresolvableFieldType_returnsNull() throws Exception {
    // getDeclaredField resolves the types of every declared field, not just the one requested, so
    // a holder with an unresolvable field type makes it throw NoClassDefFoundError - a
    // LinkageError, not a RuntimeException. Reachable via a shaded or optional dependency, or a
    // native-image holder class missing from reflect-config.json.
    ClassLoader loader =
        new HidingClassLoader(
            KafkaUtilTest.class.getClassLoader(),
            HolderWithUnresolvableFieldType.class.getName(),
            UnresolvableFieldType.class.getName());
    Object holder =
        loader
            .loadClass(HolderWithUnresolvableFieldType.class.getName())
            .getDeclaredConstructor()
            .newInstance();

    assertThat(KafkaUtil.extractMetadataFromHolder(holder)).isNull();
  }

  /** Hidden from {@link HidingClassLoader}, so its use as a field type cannot be resolved. */
  public static final class UnresolvableFieldType {}

  public static final class HolderWithUnresolvableFieldType {
    @SuppressWarnings("unused")
    private Metadata metadata;

    @SuppressWarnings("unused")
    private UnresolvableFieldType other;

    public HolderWithUnresolvableFieldType() {}
  }

  /** Defines one class itself while refusing to resolve another, simulating a missing type. */
  private static final class HidingClassLoader extends ClassLoader {
    private final String selfDefined;
    private final String hidden;

    HidingClassLoader(ClassLoader parent, String selfDefined, String hidden) {
      super(parent);
      this.selfDefined = selfDefined;
      this.hidden = hidden;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (hidden.equals(name)) {
        throw new ClassNotFoundException(name);
      }
      if (!selfDefined.equals(name)) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        byte[] bytes = readClassBytes(name);
        return defineClass(name, bytes, 0, bytes.length);
      }
    }

    private static byte[] readClassBytes(String className) throws ClassNotFoundException {
      String resource = className.replace('.', '/') + ".class";
      try (InputStream in = KafkaUtilTest.class.getClassLoader().getResourceAsStream(resource)) {
        if (in == null) {
          throw new ClassNotFoundException(className);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
          bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
      } catch (IOException e) {
        throw new ClassNotFoundException(className, e);
      }
    }
  }

  private static Metadata countingMetadata(AtomicInteger fetches, Cluster cluster) {
    // Metadata is final, so counting fetch() needs the inline mock maker.
    Metadata metadata = mock(Metadata.class);
    when(metadata.fetch())
        .thenAnswer(
            invocation -> {
              fetches.incrementAndGet();
              return cluster;
            });
    return metadata;
  }

  private static VirtualField<Consumer<?, ?>, KafkaClusterId> clusterIdField() {
    return VirtualField.find(Consumer.class, KafkaClusterId.class);
  }

  /** Enough of a {@code Consumer} to be a distinct {@code VirtualField} key. */
  private static Consumer<?, ?> stubConsumer() {
    return (Consumer<?, ?>)
        Proxy.newProxyInstance(
            Consumer.class.getClassLoader(),
            new Class<?>[] {Consumer.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                case "toString":
                  return "stubConsumer";
                default:
                  return null;
              }
            });
  }

  private static class HolderWithMetadata {
    @SuppressWarnings("unused")
    private final Metadata metadata;

    HolderWithMetadata(Metadata metadata) {
      this.metadata = metadata;
    }
  }

  private static final class SubclassHolder extends HolderWithMetadata {
    SubclassHolder(Metadata metadata) {
      super(metadata);
    }
  }
}
