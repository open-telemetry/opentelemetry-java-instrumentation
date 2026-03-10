/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.execution.ResultPath;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

class OpenTelemetryInstrumentationStateTest {

  @Test
  void setContextSetsContextForRootPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    // Act
    state.setContext(context);

    // Assert
    assertThat(state.getParentContextForPath(ResultPath.rootPath())).isEqualTo(context);
  }

  @Test
  void getContextGetsContextForRootPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    // Act
    state.setContextForPath(ResultPath.rootPath(), context);

    // Assert
    assertThat(state.getContext()).isEqualTo(context);
  }

  @Test
  void getContextReturnsCurrentContext() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    Context result;

    // Act
    try (Scope ignored = context.makeCurrent()) {
      result = state.getContext();
    }

    // Assert
    assertThat(result).isEqualTo(context);
  }

  @Test
  void getParentContextForPathReturnsCurrentContextForRootPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    Context result;

    // Act and Assert
    try (Scope ignored = context.makeCurrent()) {
      result = state.getParentContextForPath(ResultPath.rootPath());
    }

    // Assert
    assertThat(result).isEqualTo(context);
  }

  @Test
  void getParentContextForPathReturnsRootPathContextForRootPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    state.setContextForPath(ResultPath.rootPath(), context);

    // Act
    Context result = state.getParentContextForPath(ResultPath.rootPath());

    // Assert
    assertThat(result).isEqualTo(context);
  }

  @Test
  void getParentContextForPathReturnsCurrentContextForDeepPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    ResultPath resultPath = ResultPath.parse("/segment1/segment2/segment3");

    Context result;

    // Act and Assert
    try (Scope ignored = context.makeCurrent()) {
      result = state.getParentContextForPath(resultPath);
    }

    // Assert
    assertThat(result).isEqualTo(context);
  }

  @Test
  void getParentContextForPathReturnsRootPathContextForDeepPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context context = Context.root().with(ContextKey.named("New"), "Context");

    state.setContextForPath(ResultPath.rootPath(), context);

    ResultPath resultPath = ResultPath.parse("/segment1/segment2/segment3");

    // Act
    Context result = state.getParentContextForPath(resultPath);

    // Assert
    assertThat(result).isEqualTo(context);
  }

  @Test
  void getParentContextForPathReturnsParentContextForDeepPath() {
    // Arrange
    Graphql20OpenTelemetryInstrumentationState state =
        new Graphql20OpenTelemetryInstrumentationState();
    Context rootPathContext = Context.root().with(ContextKey.named("Name"), "RootPath");
    Context childContext = Context.root().with(ContextKey.named("Name"), "Child");

    state.setContextForPath(ResultPath.rootPath(), rootPathContext);
    state.setContextForPath(ResultPath.parse("/segment1/segment2"), childContext);

    // Act
    Context result =
        state.getParentContextForPath(
            ResultPath.parse("/segment1/segment2/segment3/segment4/segment5"));

    // Assert
    assertThat(result).isEqualTo(childContext);
  }
}
