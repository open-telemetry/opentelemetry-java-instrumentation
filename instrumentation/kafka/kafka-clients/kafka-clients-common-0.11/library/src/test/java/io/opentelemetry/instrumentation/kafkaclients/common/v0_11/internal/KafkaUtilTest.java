/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

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
