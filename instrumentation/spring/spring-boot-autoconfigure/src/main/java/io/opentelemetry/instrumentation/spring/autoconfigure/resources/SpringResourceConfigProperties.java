/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpExporterProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.expression.ExpressionParser;

public class SpringResourceConfigProperties implements ConfigProperties {
  private final Environment environment;

  private final ExpressionParser parser;
  private final OtlpExporterProperties otlpExporterProperties;

  public SpringResourceConfigProperties(
      Environment environment,
      ExpressionParser parser,
      OtlpExporterProperties otlpExporterProperties) {
    this.environment = environment;
    this.parser = parser;
    this.otlpExporterProperties = otlpExporterProperties;
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

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String name) {
    return (List<String>) environment.getProperty(name, List.class);
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    String value = getString(name);
    if (value == null || value.isEmpty()) {
      return null;
    }
    String unitString = getUnitString(value);
    // TODO: Environment variables have unknown encoding.  `trim()` may cut codepoints oddly
    // but likely we'll fail for malformed unit string either way.
    String numberString = value.substring(0, value.length() - unitString.length());
    try {
      long rawNumber = Long.parseLong(numberString.trim());
      TimeUnit unit = getDurationUnit(unitString.trim());
      return Duration.ofMillis(TimeUnit.MILLISECONDS.convert(rawNumber, unit));
    } catch (NumberFormatException ex) {
      throw new ConfigurationException(
          "Invalid duration property "
              + name
              + "="
              + value
              + ". Expected number, found: "
              + numberString,
          ex);
    } catch (ConfigurationException ex) {
      throw new ConfigurationException(
          "Invalid duration property " + name + "=" + value + ". " + ex.getMessage());
    }
  }

  /** Returns the TimeUnit associated with a unit string. Defaults to milliseconds. */
  private static TimeUnit getDurationUnit(String unitString) {
    switch (unitString) {
      case "": // Fallthrough expected
      case "ms":
        return TimeUnit.MILLISECONDS;
      case "s":
        return TimeUnit.SECONDS;
      case "m":
        return TimeUnit.MINUTES;
      case "h":
        return TimeUnit.HOURS;
      case "d":
        return TimeUnit.DAYS;
      default:
        throw new ConfigurationException("Invalid duration string, found: " + unitString);
    }
  }

  /**
   * Fragments the 'units' portion of a config value from the 'value' portion.
   *
   * <p>E.g. "1ms" would return the string "ms".
   */
  private static String getUnitString(String rawValue) {
    int lastDigitIndex = rawValue.length() - 1;
    while (lastDigitIndex >= 0) {
      char c = rawValue.charAt(lastDigitIndex);
      if (Character.isDigit(c)) {
        break;
      }
      lastDigitIndex -= 1;
    }
    // Pull everything after the last digit.
    return rawValue.substring(lastDigitIndex + 1);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String name) {
    // maps from config properties are not supported by Environment, so we have to fake it
    switch (name) {
      case "otel.exporter.otlp.headers":
        return otlpExporterProperties.getHeaders();
      case "otel.exporter.otlp.logs.headers":
        return otlpExporterProperties.getLogs().getHeaders();
      case "otel.exporter.otlp.metrics.headers":
        return otlpExporterProperties.getMetrics().getHeaders();
      case "otel.exporter.otlp.traces.headers":
        return otlpExporterProperties.getTraces().getHeaders();
      default:
        break;
    }

    String value = environment.getProperty(name);
    if (value == null) {
      return Collections.emptyMap();
    }
    return (Map<String, String>) parser.parseExpression(value).getValue();
  }
}
