/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DeclarativeConfigUtil {

  public static Optional<Boolean> getBoolean(OpenTelemetry openTelemetry, String... propertyPath) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyPath);
    return Optional.ofNullable(node != null ? node.getBoolean(leaf(propertyPath)) : null);
  }

  public static Optional<String> getString(OpenTelemetry openTelemetry, String... propertyPath) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyPath);
    return Optional.ofNullable(node != null ? node.getString(leaf(propertyPath)) : null);
  }

  public static Optional<Integer> getInt(OpenTelemetry openTelemetry, String... propertyPath) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyPath);
    return Optional.ofNullable(node != null ? node.getInt(leaf(propertyPath)) : null);
  }

  public static Optional<List<String>> getList(
      OpenTelemetry openTelemetry, String... propertyPath) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyPath);
    if (node != null) {
      List<String> list = node.getScalarList(leaf(propertyPath), String.class);
      return Optional.ofNullable(list);
    }
    return Optional.empty();
  }

  public static Optional<DeclarativeConfigProperties> getStructuredConfig(
      OpenTelemetry openTelemetry, String... propertyPath) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyPath);
    return Optional.ofNullable(node != null ? node.getStructured(leaf(propertyPath)) : null);
  }

  public static Optional<List<DeclarativeConfigProperties>> getStructuredList(
      OpenTelemetry openTelemetry, String... propertyPath) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyPath);
    return Optional.ofNullable(node != null ? node.getStructuredList(leaf(propertyPath)) : null);
  }

  @Nullable
  private static DeclarativeConfigProperties getDeclarativeConfigNode(
      OpenTelemetry openTelemetry, String... propertyPath) {

    if (!(openTelemetry instanceof ExtendedOpenTelemetry)) {
      return null;
    }
    ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
    ConfigProvider configProvider = extendedOpenTelemetry.getConfigProvider();
    if (configProvider == null) {
      return null;
    }
    return getConfigNode(configProvider, propertyPath);
  }

  private static DeclarativeConfigProperties getConfigNode(
      ConfigProvider configProvider, String... propertyPath) {

    DeclarativeConfigProperties instrumentationConfig = configProvider.getInstrumentationConfig();
    if (instrumentationConfig == null) {
      return empty();
    }
    DeclarativeConfigProperties node = instrumentationConfig.getStructured("java", empty());
    for (int i = 0; i < propertyPath.length - 1; i++) {
      node = node.getStructured(propertyPath[i], empty());
    }
    return node;
  }

  private static String leaf(String[] propertyPath) {
    return propertyPath[propertyPath.length - 1];
  }

  private DeclarativeConfigUtil() {}
}
