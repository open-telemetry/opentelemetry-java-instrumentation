/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
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
  void unresolvedClusterId_keepsReadingUntilTheBrokerReports() {
    // Never terminal and never rate limited per client: a span reads at its start and again at its
    // end, and only the second read can see the broker's first metadata response. Turning either
    // read away -- on a budget or on a timer a neighbouring span could spend -- drops the
    // attribute.
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
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();

    assertThat(KafkaUtil.initializeClusterId(client, field, new HolderWithMetadata(metadata)))
        .isNull();
    KafkaClusterId published = field.get(client);
    for (int i = 0; i < 20; i++) {
      assertThat(KafkaUtil.readClusterId(published)).isNull();
    }
    assertThat(fetches.get()).isEqualTo(21);

    reported.set(new Cluster("test-cluster", emptyList(), emptyList(), emptySet(), emptySet()));

    assertThat(KafkaUtil.readClusterId(published)).isEqualTo("test-cluster");
  }

  @Test
  void resolvedAndUnavailableClusterId_neverReadMetadata() {
    // Once the id is known there is nothing left to read, and UNAVAILABLE holds no Metadata at all.
    AtomicInteger fetches = new AtomicInteger();
    KafkaClusterId resolved = KafkaClusterId.of(countingMetadata(fetches, Cluster.empty()));
    resolved.resolve("test-cluster");

    assertThat(KafkaUtil.readClusterId(resolved)).isEqualTo("test-cluster");
    assertThat(KafkaUtil.readClusterId(KafkaClusterId.UNAVAILABLE)).isNull();
    assertThat(fetches.get()).isZero();
    // The shared UNAVAILABLE singleton must never pick up another client's id.
    assertThat(KafkaClusterId.UNAVAILABLE.clusterId()).isNull();
  }

  @Test
  void initializeClusterId_publishesTheHolderBeforeReadingMetadata() {
    // A concurrent send has to find the holder, so it must reach the VirtualField before anything
    // touches Metadata.
    Consumer<?, ?> client = stubConsumer();
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();
    AtomicReference<KafkaClusterId> publishedWhenRead = new AtomicReference<>();
    Metadata metadata = mock(Metadata.class);
    when(metadata.fetch())
        .thenAnswer(
            invocation -> {
              publishedWhenRead.set(field.get(client));
              return Cluster.empty();
            });

    assertThat(KafkaUtil.initializeClusterId(client, field, new HolderWithMetadata(metadata)))
        .isNull();

    assertThat(publishedWhenRead.get()).isNotNull().isSameAs(field.get(client));
  }

  @Test
  void initializeClusterId_noMetadata_isTerminal() {
    Consumer<?, ?> client = stubConsumer();
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();

    assertThat(KafkaUtil.initializeClusterId(client, field, new Object())).isNull();

    assertThat(field.get(client)).isSameAs(KafkaClusterId.UNAVAILABLE);
  }

  @Test
  void resolvedClusterIdIsServedWithoutReadingMetadataAgain() {
    AtomicInteger fetches = new AtomicInteger();
    Metadata metadata =
        countingMetadata(
            fetches, new Cluster("test-cluster", emptyList(), emptyList(), emptySet(), emptySet()));
    Consumer<?, ?> client = stubConsumer();
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();

    assertThat(KafkaUtil.initializeClusterId(client, field, new HolderWithMetadata(metadata)))
        .isEqualTo("test-cluster");

    KafkaClusterId published = field.get(client);
    assertThat(published.clusterId()).isEqualTo("test-cluster");
    assertThat(KafkaUtil.readClusterId(published)).isEqualTo("test-cluster");
    assertThat(fetches.get()).isEqualTo(1);
  }

  @Test
  void racingSendsStillResolveTheClusterId() throws InterruptedException {
    // VirtualField has no compare-and-set, so a burst of first sends publishes more than one holder
    // and a late one can land on a holder another thread already resolved. Whichever holder ends up
    // in the field must still resolve, or every later span loses the attribute.
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
    VirtualField<Consumer<?, ?>, KafkaClusterId> field = clusterIdField();
    HolderWithMetadata holder = new HolderWithMetadata(metadata);

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
                // The getClusterId() path, while the broker is still silent.
                KafkaClusterId cached = field.get(client);
                if (cached == null) {
                  KafkaUtil.initializeClusterId(client, field, holder);
                } else {
                  KafkaUtil.readClusterId(cached);
                }
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

    // No thread can have invented an id while the broker reported none.
    assertThat(field.get(client).clusterId()).isNull();
    // One read per send at most, never a retry loop.
    assertThat(fetches.get()).isLessThanOrEqualTo(threads);

    reported.set(new Cluster("test-cluster", emptyList(), emptyList(), emptySet(), emptySet()));

    assertThat(KafkaUtil.readClusterId(field.get(client))).isEqualTo("test-cluster");
    assertThat(field.get(client).clusterId()).isEqualTo("test-cluster");
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
