/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.expression.ExpressionParser;

public class SpringResourceConfigProperties implements ConfigProperties {
  private final Environment environment;

  private final ExpressionParser parser;

  public SpringResourceConfigProperties(Environment environment, ExpressionParser parser) {
    this.environment = environment;
    this.parser = parser;
  }

  @Nullable
  @Override
  public String getString(String name) {
    return environment.getProperty(name, String.class);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return environment.getProperty(name, Boolean.class);
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return environment.getProperty(name, Integer.class);
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return environment.getProperty(name, Long.class);
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return environment.getProperty(name, Double.class);
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    return environment.getProperty(name, Duration.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String name) {
    return (List<String>) environment.getProperty(name, List.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String name) {
    String value = environment.getProperty(name);
    return (Map<String, String>) parser.parseExpression(Objects.requireNonNull(value)).getValue();
  }
}
