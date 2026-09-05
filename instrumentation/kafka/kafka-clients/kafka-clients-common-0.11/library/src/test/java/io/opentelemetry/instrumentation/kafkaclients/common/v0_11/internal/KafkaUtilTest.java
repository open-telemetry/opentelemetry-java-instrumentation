/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.kafka.clients.Metadata;
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
    // Fresh Metadata with no update — fetch() returns the bootstrap cluster whose clusterId is
    // null.
    Metadata metadata = new Metadata(0, Long.MAX_VALUE, false);
    assertThat(KafkaUtil.clusterIdFromMetadata(metadata)).isNull();
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
