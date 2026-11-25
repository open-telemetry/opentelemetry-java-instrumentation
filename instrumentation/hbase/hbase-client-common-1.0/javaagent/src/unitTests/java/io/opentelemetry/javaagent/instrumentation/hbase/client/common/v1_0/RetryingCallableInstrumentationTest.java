/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0.HbaseClientState.currentTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0.RetryingCallableInstrumentation.RpcCallAdvice.onEnter;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0.RetryingCallableInstrumentation.RpcCallAdvice.onExit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.hadoop.hbase.TableName;
import org.junit.jupiter.api.Test;

class RetryingCallableInstrumentationTest {

  @Test
  void restoresEnclosingTableAfterNestedFailure() {
    TableName outer = TableName.valueOf("outer");
    TableName inner = TableName.valueOf("inner");
    TableName initial = currentTableName().get();
    TableName beforeOuter = onEnter(outer);
    try {
      assertThatThrownBy(
              () -> {
                TableName beforeInner = onEnter(inner);
                try {
                  assertThat(HbaseClientState.getTableName()).isEqualTo(inner);
                  throw new IllegalStateException("nested call failed");
                } finally {
                  onExit(beforeInner);
                }
              })
          .isInstanceOf(IllegalStateException.class);
      assertThat(HbaseClientState.getTableName()).isEqualTo(outer);
    } finally {
      onExit(beforeOuter);
    }
    assertThat(currentTableName().get()).isSameAs(initial);
  }

  @Test
  void unknownTablePreservesEnclosingTable() {
    TableName outer = TableName.valueOf("outer");
    TableName beforeOuter = onEnter(outer);
    try {
      TableName beforeInner = onEnter(null);
      try {
        assertThat(HbaseClientState.getTableName()).isEqualTo(outer);
      } finally {
        onExit(beforeInner);
      }
      assertThat(HbaseClientState.getTableName()).isEqualTo(outer);
    } finally {
      onExit(beforeOuter);
    }
  }
}
