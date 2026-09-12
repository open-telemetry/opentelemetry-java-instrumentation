/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.grpc.Context;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ContextStorageBridgeTest {

  @Test
  void constructorWithBothArgsSetsBothFields() throws Exception {
    Context.Storage originalStorage = mock(Context.Storage.class);
    ContextStorageBridge bridge = new ContextStorageBridge(true, originalStorage);

    assertThat(getPropagateGrpcDeadline(bridge)).isTrue();
    assertThat(getOriginalStorage(bridge)).isSameAs(originalStorage);
  }

  @Test
  void constructorWithBothArgsSupportsDisablingPropagation() throws Exception {
    Context.Storage originalStorage = mock(Context.Storage.class);
    ContextStorageBridge bridge = new ContextStorageBridge(false, originalStorage);

    assertThat(getPropagateGrpcDeadline(bridge)).isFalse();
    assertThat(getOriginalStorage(bridge)).isSameAs(originalStorage);
  }

  private static boolean getPropagateGrpcDeadline(ContextStorageBridge bridge) throws Exception {
    Field field = ContextStorageBridge.class.getDeclaredField("propagateGrpcDeadline");
    field.setAccessible(true);
    return (boolean) field.get(bridge);
  }

  private static Context.Storage getOriginalStorage(ContextStorageBridge bridge) throws Exception {
    Field field = ContextStorageBridge.class.getDeclaredField("originalStorage");
    field.setAccessible(true);
    return (Context.Storage) field.get(bridge);
  }
}
