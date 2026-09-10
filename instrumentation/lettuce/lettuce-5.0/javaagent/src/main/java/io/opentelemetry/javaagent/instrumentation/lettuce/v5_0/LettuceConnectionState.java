/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetSocketAddress;
import java.util.Objects;
import javax.annotation.Nullable;

public final class LettuceConnectionState {
  @Nullable public final InetSocketAddress serverAddress;
  @Nullable public final Integer databaseIndex;
  @Nullable public final RedisServerTarget serverTarget;

  public LettuceConnectionState(
      @Nullable InetSocketAddress serverAddress,
      @Nullable Integer databaseIndex,
      @Nullable RedisServerTarget serverTarget) {
    this.serverAddress = serverAddress;
    this.databaseIndex = databaseIndex;
    this.serverTarget = serverTarget;
  }

  @Nullable
  public static LettuceConnectionState withServerAddress(
      @Nullable LettuceConnectionState state, @Nullable InetSocketAddress serverAddress) {
    if (state == null) {
      return serverAddress == null ? null : new LettuceConnectionState(serverAddress, null, null);
    }
    if (Objects.equals(state.serverAddress, serverAddress)) {
      return state;
    }
    return new LettuceConnectionState(serverAddress, state.databaseIndex, state.serverTarget);
  }

  @Nullable
  public static LettuceConnectionState withServerTarget(
      @Nullable LettuceConnectionState state, @Nullable RedisServerTarget serverTarget) {
    if (state == null) {
      return serverTarget == null ? null : new LettuceConnectionState(null, null, serverTarget);
    }
    if (sameServerTarget(state.serverTarget, serverTarget)) {
      return state;
    }
    if (state.serverAddress == null && state.databaseIndex == null && serverTarget == null) {
      return null;
    }
    return new LettuceConnectionState(state.serverAddress, state.databaseIndex, serverTarget);
  }

  public static boolean sameServerTarget(
      @Nullable RedisServerTarget first, @Nullable RedisServerTarget second) {
    if (first == second) {
      return true;
    }
    return first != null
        && second != null
        && first.getAddress().equals(second.getAddress())
        && Objects.equals(first.getPort(), second.getPort());
  }
}
