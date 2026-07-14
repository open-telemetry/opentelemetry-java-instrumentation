/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.hbase.testing.AbstractHbaseTest;
import java.io.IOException;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledIfSystemProperty(named = "java.vm.name", matches = ".*OpenJ9.*")
class HbaseClient20Test extends AbstractHbaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected String instrumentationName() {
    return "io.opentelemetry.hbase-client-2.0";
  }

  @Override
  protected void createTable(Admin admin) throws IOException {
    ColumnFamilyDescriptor columnFamilyDescriptor =
        ColumnFamilyDescriptorBuilder.newBuilder(COLUMN_FAMILY).build();
    TableDescriptor tableDescriptor =
        TableDescriptorBuilder.newBuilder(TABLE_NAME)
            .setColumnFamily(columnFamilyDescriptor)
            .build();
    admin.createTable(tableDescriptor, new byte[][] {Bytes.toBytes("m")});
  }

  @Override
  protected byte[] checkAndMutateCheckedRowKey() {
    return Bytes.toBytes(ROW_1);
  }

  @Override
  protected void checkAndMutate(Table table, byte[] checkedRowKey, RowMutations rowMutations)
      throws IOException {
    table
        .checkAndMutate(checkedRowKey, COLUMN_FAMILY)
        .qualifier(Bytes.toBytes("col1"))
        .ifMatches(CompareOperator.EQUAL, Bytes.toBytes("col1_val_1"))
        .thenMutate(rowMutations);
  }
}
