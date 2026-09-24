/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.Objects;
import javax.annotation.Nullable;

public final class LettuceConnectionState {
  private static final VirtualField<DefaultEndpoint, LettuceConnectionState> ENDPOINT_STATE =
      VirtualField.find(DefaultEndpoint.class, LettuceConnectionState.class);

  private static final VirtualField<RedisChannelHandler<?, ?>, LettuceConnectionState>
      CONNECTION_STATE = VirtualField.find(RedisChannelHandler.class, LettuceConnectionState.class);

  private static final VirtualField<RedisCommand<?, ?, ?>, LettuceConnectionState> COMMAND_STATE =
      VirtualField.find(RedisCommand.class, LettuceConnectionState.class);

  @Nullable final InetSocketAddress serverAddress;
  @Nullable final Integer databaseIndex;
  @Nullable final RedisServerTarget serverTarget;

  LettuceConnectionState(
      @Nullable InetSocketAddress serverAddress,
      @Nullable Integer databaseIndex,
      @Nullable RedisServerTarget serverTarget) {
    this.serverAddress = serverAddress;
    this.databaseIndex = databaseIndex;
    this.serverTarget = serverTarget;
  }

  public static void captureEndpoint(
      DefaultEndpoint endpoint,
      @Nullable InetSocketAddress serverAddress,
      @Nullable Integer databaseIndex,
      @Nullable RedisServerTarget serverTarget) {
    ENDPOINT_STATE.set(
        endpoint, new LettuceConnectionState(serverAddress, databaseIndex, serverTarget));
  }

  public static void captureEndpointAndConnection(
      DefaultEndpoint endpoint,
      @Nullable RedisChannelHandler<?, ?> connection,
      @Nullable InetSocketAddress serverAddress,
      @Nullable Integer databaseIndex,
      @Nullable RedisServerTarget serverTarget) {
    LettuceConnectionState state =
        new LettuceConnectionState(serverAddress, databaseIndex, serverTarget);
    ENDPOINT_STATE.set(endpoint, state);
    if (connection != null) {
      CONNECTION_STATE.set(connection, state);
    }
  }

  public static void copy(StatefulConnection<?, ?> connection, RedisCommand<?, ?, ?> command) {
    if (!(connection instanceof RedisChannelHandler)) {
      return;
    }

    RedisChannelHandler<?, ?> connectionHandler = (RedisChannelHandler<?, ?>) connection;
    RedisServerTarget commandTarget = serverTarget(command);
    LettuceConnectionState connectionState = CONNECTION_STATE.get(connectionHandler);
    COMMAND_STATE.set(
        command,
        commandTarget == null ? connectionState : withServerTarget(connectionState, commandTarget));
  }

  public static void copy(
      DefaultEndpoint endpoint,
      RedisCommand<?, ?, ?> command,
      @Nullable RedisServerTarget commandTarget) {
    LettuceConnectionState endpointState = ENDPOINT_STATE.get(endpoint);
    COMMAND_STATE.set(
        command,
        commandTarget == null ? endpointState : withServerTarget(endpointState, commandTarget));
  }

  public static void updateServerAddress(
      DefaultEndpoint endpoint, @Nullable InetSocketAddress serverAddress) {
    ENDPOINT_STATE.set(endpoint, withServerAddress(ENDPOINT_STATE.get(endpoint), serverAddress));
  }

  public static void updateServerTarget(
      RedisChannelHandler<?, ?> connection, @Nullable RedisServerTarget serverTarget) {
    CONNECTION_STATE.set(
        connection, withServerTarget(CONNECTION_STATE.get(connection), serverTarget));
  }

  @Nullable
  static LettuceConnectionState get(DefaultEndpoint endpoint) {
    return ENDPOINT_STATE.get(endpoint);
  }

  @Nullable
  static InetSocketAddress serverAddress(DefaultEndpoint endpoint) {
    LettuceConnectionState state = ENDPOINT_STATE.get(endpoint);
    return state == null ? null : state.serverAddress;
  }

  @Nullable
  static InetSocketAddress serverAddress(RedisCommand<?, ?, ?> command) {
    LettuceConnectionState state = COMMAND_STATE.get(command);
    return state == null ? null : state.serverAddress;
  }

  @Nullable
  static Integer databaseIndex(RedisCommand<?, ?, ?> command) {
    LettuceConnectionState state = COMMAND_STATE.get(command);
    return state == null ? null : state.databaseIndex;
  }

  @Nullable
  static RedisServerTarget serverTarget(DefaultEndpoint endpoint) {
    LettuceConnectionState state = ENDPOINT_STATE.get(endpoint);
    return state == null ? null : state.serverTarget;
  }

  @Nullable
  public static RedisServerTarget serverTarget(RedisCommand<?, ?, ?> command) {
    LettuceConnectionState state = COMMAND_STATE.get(command);
    return state == null ? null : state.serverTarget;
  }

  @Nullable
  private static LettuceConnectionState withServerAddress(
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
  static LettuceConnectionState withServerTarget(
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

  static boolean sameServerTarget(
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
