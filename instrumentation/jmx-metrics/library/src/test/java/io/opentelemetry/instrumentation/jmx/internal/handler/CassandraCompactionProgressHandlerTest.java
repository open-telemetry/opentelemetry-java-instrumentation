/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.handler;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.jmx.internal.ExperimentalJmxMetricHandler.Detector;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CassandraCompactionProgressHandlerTest {

  private static final String ATTR_TASK_TYPE = "cassandra.compaction.task_type";
  private static final String ATTR_KEYSPACE = "cassandra.keyspace";
  private static final String ATTR_TABLE = "cassandra.table";

  private MBeanServerConnection connection;
  private ObjectName objectName;

  @BeforeEach
  void setUp() throws Exception {
    connection = mock(MBeanServerConnection.class);
    objectName = new ObjectName("org.apache.cassandra.db:type=CompactionManager");
  }

  @Test
  void groupsByCompositeKey() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("Compaction", "ks1", "cf1", "100", "200"),
                compactionEntry("Compaction", "ks1", "cf1", "50", "150"),
                compactionEntry("Compaction", "ks2", "cf2", "10", "100")));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(2);
    assertThat(groups.get(attrs("compaction", "ks1", "cf1"))).containsExactly(150L, 350L);
    assertThat(groups.get(attrs("compaction", "ks2", "cf2"))).containsExactly(10L, 100L);
  }

  @Test
  void skipsEntriesMissingDimensionFields() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("Compaction", "ks1", "cf1", "10", "100"),
                compactionEntry(null, "ks1", "cf1", "5", "50"),
                compactionEntry("Compaction", null, "cf1", "5", "50"),
                compactionEntry("Compaction", "ks1", null, "5", "50")));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(attrs("compaction", "ks1", "cf1"))).containsExactly(10L, 100L);
  }

  @Test
  void skipsEntriesWithNonByteUnits() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("Compaction", "ks1", "cf1", "10", "100", "bytes"),
                compactionEntry("Validation", "ks1", "cf1", "5", "50", "keys"),
                compactionEntry("Anti-Compaction", "ks1", "cf1", "5", "50", "ranges"),
                compactionEntry("Compaction", "ks2", "cf2", "5", "50", null)));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(attrs("compaction", "ks1", "cf1"))).containsExactly(10L, 100L);
  }

  @Test
  void skipsEntriesWithValuesExceedingLongRange() throws Exception {
    String big = "99999999999999999999";
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(singletonList(compactionEntry("Compaction", "ks", "cf", big, big)));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).isEmpty();
  }

  @Test
  void skipsEntriesWithMalformedValues() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("Compaction", "ks1", "cf1", "not-a-number", "100"),
                compactionEntry("Compaction", "ks2", "cf2", "50", "100")));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(attrs("compaction", "ks2", "cf2"))).containsExactly(50L, 100L);
  }

  @Test
  void normalizesTaskType() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("Upgrade sstables", "ks1", "cf1", "10", "100"),
                compactionEntry("Secondary index build", "ks1", "cf2", "20", "200")));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(2);
    assertThat(groups.get(attrs("upgrade_sstables", "ks1", "cf1"))).containsExactly(10L, 100L);
    assertThat(groups.get(attrs("secondary_index_build", "ks1", "cf2"))).containsExactly(20L, 200L);
  }

  @Test
  void returnsEmptyMapOnException() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenThrow(new RuntimeException("connection lost"));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).isEmpty();
  }

  @Test
  void handlerNameIsStable() {
    assertThat(new CassandraCompactionProgressHandler().getName())
        .isEqualTo("cassandra-compaction-progress");
  }

  @Test
  void mergesGroupsAcrossMultipleObjectNames() throws Exception {
    ObjectName objectName2 = new ObjectName("org.apache.cassandra.db:type=CompactionManager,id=2");
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(singletonList(compactionEntry("Compaction", "ks1", "cf1", "100", "200")));
    when(connection.getAttribute(objectName2, "Compactions"))
        .thenReturn(singletonList(compactionEntry("Compaction", "ks1", "cf1", "50", "150")));

    Detector detector =
        new Detector() {
          @Override
          public MBeanServerConnection getConnection() {
            return connection;
          }

          @Override
          public Collection<ObjectName> getObjectNames() {
            return asList(objectName, objectName2);
          }
        };

    Map<Attributes, long[]> merged = CassandraCompactionProgressHandler.queryGroups(() -> detector);

    assertThat(merged).hasSize(1);
    assertThat(merged.get(attrs("compaction", "ks1", "cf1"))).containsExactly(150L, 350L);
  }

  private static Map<String, String> compactionEntry(
      String taskType, String keyspace, String columnfamily, String completed, String total) {
    return compactionEntry(taskType, keyspace, columnfamily, completed, total, "bytes");
  }

  private static Map<String, String> compactionEntry(
      String taskType,
      String keyspace,
      String columnfamily,
      String completed,
      String total,
      String unit) {
    Map<String, String> entry = new HashMap<>();
    entry.put("taskType", taskType);
    entry.put("keyspace", keyspace);
    entry.put("columnfamily", columnfamily);
    entry.put("completed", completed);
    entry.put("total", total);
    entry.put("unit", unit);
    return entry;
  }

  private static Attributes attrs(String taskType, String keyspace, String columnFamily) {
    return Attributes.builder()
        .put(ATTR_TASK_TYPE, taskType)
        .put(ATTR_KEYSPACE, keyspace)
        .put(ATTR_TABLE, columnFamily)
        .build();
  }
}
