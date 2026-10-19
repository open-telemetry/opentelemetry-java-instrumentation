/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.hadoop.hbase.client.MetricsConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HbaseCallUtilIndyTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void keepsHelperAndStateOutOfApplicationClassLoader() {
    Call call =
        new Call(1, null, null, null, null, 0, 0, ignored -> {}, new MetricsConnection.CallStats());
    ClassLoader applicationLoader = Call.class.getClassLoader();
    assertThatThrownBy(
            () ->
                applicationLoader.loadClass(
                    "io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseCallUtil"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(
            () ->
                applicationLoader.loadClass(
                    "io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(
            () ->
                applicationLoader.loadClass(
                    "io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest"))
        .isInstanceOf(ClassNotFoundException.class);

    call.setResponse(null, null);
  }
}
