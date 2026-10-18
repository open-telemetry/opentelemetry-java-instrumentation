/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.MetricsConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryCallUtilIndyTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void exchangesIsolatedStateWithApplicationLoadedHelper() throws Exception {
    Call call =
        new Call(
            1, null, null, null, null, 0, 0, ignored -> {}, new MetricsConnection.CallStats());
    ClassLoader applicationLoader = Call.class.getClassLoader();
    Class<?> callUtil =
        Class.forName("org.apache.hadoop.hbase.ipc.OpenTelemetryCallUtil", true, applicationLoader);
    assertThat(callUtil.getClassLoader()).isSameAs(applicationLoader);

    // Resolve through the injected helper to exercise its linkage to the exposed, isolated state.
    Method getAndClear = callUtil.getMethod("getAndClearRequestAndContext", Object.class);
    Class<?> stateType = getAndClear.getReturnType();
    Class<?> requestType = stateType.getMethod("getRequest").getReturnType();
    Class<?> scopeType = stateType.getMethod("getScope").getReturnType();
    Class<?> contextType = stateType.getMethod("getContext").getReturnType();
    assertThat(stateType.getClassLoader()).isNotSameAs(applicationLoader);
    assertThat(requestType.getClassLoader()).isSameAs(stateType.getClassLoader());
    assertThat(applicationLoader.loadClass(stateType.getName())).isSameAs(stateType);
    assertThat(applicationLoader.loadClass(requestType.getName())).isSameAs(requestType);

    Object request =
        requestType
            .getMethod("create", String.class, TableName.class, String.class, Long.class)
            .invoke(null, "Get", null, "logical-target", null);
    Object state =
        stateType
            .getMethod("create", requestType, scopeType, contextType)
            .invoke(
                null,
                request,
                scopeType.getMethod("noop").invoke(null),
                contextType.getMethod("root").invoke(null));

    callUtil.getMethod("setRequestAndContext", Object.class, stateType).invoke(null, call, state);
    InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234);
    callUtil
        .getMethod("setNetworkPeer", Object.class, SocketAddress.class)
        .invoke(null, call, peer);

    assertThat(requestType.getMethod("getNetworkPeerInetSocketAddress").invoke(request))
        .isSameAs(peer);
    assertThat(getAndClear.invoke(null, call)).isSameAs(state);
    assertThat(getAndClear.invoke(null, call)).isNull();
  }
}
