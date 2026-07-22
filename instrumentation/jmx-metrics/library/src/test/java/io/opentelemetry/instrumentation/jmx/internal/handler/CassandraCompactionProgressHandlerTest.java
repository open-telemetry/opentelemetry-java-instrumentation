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
                compactionEntry("COMPACTION", "ks1", "cf1", "100", "200"),
                compactionEntry("COMPACTION", "ks1", "cf1", "50", "150"),
                compactionEntry("COMPACTION", "ks2", "cf2", "10", "100")));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(2);
    assertThat(groups.get(attrs("COMPACTION", "ks1", "cf1"))).containsExactly(150L, 350L);
    assertThat(groups.get(attrs("COMPACTION", "ks2", "cf2"))).containsExactly(10L, 100L);
  }

  @Test
  void skipsEntriesMissingDimensionFields() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("COMPACTION", "ks1", "cf1", "10", "100"),
                compactionEntry("COMPACTION", null, "cf1", "5", "50"),
                compactionEntry("COMPACTION", "ks1", null, "5", "50")));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(attrs("COMPACTION", "ks1", "cf1"))).containsExactly(10L, 100L);
  }

  @Test
  void skipsEntriesWithNonByteUnits() throws Exception {
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(
            asList(
                compactionEntry("COMPACTION", "ks1", "cf1", "10", "100", "bytes"),
                compactionEntry("VALIDATION", "ks1", "cf1", "5", "50", "keys"),
                compactionEntry("ANTICOMPACTION", "ks1", "cf1", "5", "50", "ranges"),
                compactionEntry("COMPACTION", "ks2", "cf2", "5", "50", null)));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(attrs("COMPACTION", "ks1", "cf1"))).containsExactly(10L, 100L);
  }

  @Test
  void skipsEntriesWithValuesExceedingLongRange() throws Exception {
    // values larger than Long.MAX_VALUE cannot be safely cast to long — entry must be skipped
    String big = "99999999999999999999";
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(singletonList(compactionEntry("COMPACTION", "ks", "cf", big, big)));

    Map<Attributes, long[]> groups =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);

    assertThat(groups).isEmpty();
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
        .isEqualTo(CassandraCompactionProgressHandler.HANDLER_NAME);
  }

  @Test
  void mergesGroupsAcrossMultipleObjectNames() throws Exception {
    ObjectName objectName2 = new ObjectName("org.apache.cassandra.db:type=CompactionManager,id=2");
    when(connection.getAttribute(objectName, "Compactions"))
        .thenReturn(singletonList(compactionEntry("COMPACTION", "ks1", "cf1", "100", "200")));
    when(connection.getAttribute(objectName2, "Compactions"))
        .thenReturn(singletonList(compactionEntry("COMPACTION", "ks1", "cf1", "50", "150")));

    Map<Attributes, long[]> first =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName);
    Map<Attributes, long[]> second =
        CassandraCompactionProgressHandler.queryCompactions(connection, objectName2);

    // Simulate what queryGroups does: merge across ObjectNames using the same merge function
    Map<Attributes, long[]> merged = new HashMap<>(first);
    second.forEach(
        (attrs, values) ->
            merged.merge(attrs, values, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]}));

    assertThat(merged).hasSize(1);
    assertThat(merged.get(attrs("COMPACTION", "ks1", "cf1"))).containsExactly(150L, 350L);
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
