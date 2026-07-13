/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.hbase.testing.AbstractHbaseTest;
import java.io.IOException;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledIfSystemProperty(named = "java.vm.name", matches = ".*OpenJ9.*")
class HbaseClient14Test extends AbstractHbaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected String instrumentationName() {
    return "io.opentelemetry.hbase-client-1.4";
  }

  @Override
  protected void createTable(Admin admin) throws IOException {
    HTableDescriptor tableDescriptor = new HTableDescriptor(TABLE_NAME);
    tableDescriptor.addFamily(new HColumnDescriptor(COLUMN_FAMILY));
    admin.createTable(tableDescriptor);
  }

  @Override
  protected int getTimeoutClientRetriesNumber() {
    return 1;
  }

  @Override
  protected String putOperation() {
    return emitStableDatabaseSemconv() ? MUTATE : MULTI;
  }

  @Override
  protected byte[] checkAndMutateCheckedRowKey() {
    return Bytes.toBytes(CHECK_MUTATE_ROW);
  }

  @Override
  protected byte[] checkAndMutateMutatedRowKey() {
    return checkAndMutateCheckedRowKey();
  }
}
