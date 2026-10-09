/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.currentRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.currentTableName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import org.apache.hadoop.hbase.TableName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HbaseClientStateTest {

  @AfterEach
  void clearState() {
    currentTableName().restore(null);
    currentRequestAndContext().restore(null);
  }

  @Test
  void nestedTableNameRestoresOuterOnExceptionalExit() {
    TableName outer = TableName.valueOf("outer");
    TableName nested = TableName.valueOf("nested");
    TableName previousOuter = currentTableName().set(outer);
    try {
      assertThatThrownBy(
              () -> {
                TableName previous = currentTableName().set(nested);
                try {
                  assertThat(HbaseClientState.getTableName()).isSameAs(nested);
                  throw new IllegalStateException("call failed");
                } finally {
                  currentTableName().restore(previous);
                }
              })
          .isInstanceOf(IllegalStateException.class);
      assertThat(HbaseClientState.getTableName()).isSameAs(outer);
    } finally {
      currentTableName().restore(previousOuter);
    }
    assertThat(HbaseClientState.getTableName()).isNull();
  }

  @Test
  void nestedRequestCarriesPreviousStateWithoutReplacingOuterRequest() {
    RequestAndContext outer = requestAndContext();
    RequestAndContext nested = requestAndContext();
    outer.setPrevious(currentRequestAndContext().set(outer));
    try {
      nested.setPrevious(currentRequestAndContext().set(nested));
      try {
        assertThat(HbaseClientState.getRequestAndContext()).isSameAs(nested);
        assertThat(nested.getPrevious()).isSameAs(outer);
      } finally {
        currentRequestAndContext().restore(nested.getPrevious());
        nested.setPrevious(null);
      }
      assertThat(HbaseClientState.getRequestAndContext()).isSameAs(outer);
      assertThat(nested.getPrevious()).isNull();
    } finally {
      currentRequestAndContext().restore(outer.getPrevious());
      outer.setPrevious(null);
    }
    assertThat(HbaseClientState.getRequestAndContext()).isNull();
  }

  private static RequestAndContext requestAndContext() {
    return RequestAndContext.create(
        HbaseRequest.create("Get", null, null, null), () -> {}, Context.root());
  }
}
