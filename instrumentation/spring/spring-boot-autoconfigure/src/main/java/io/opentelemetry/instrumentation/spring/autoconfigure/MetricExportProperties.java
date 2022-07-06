/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import java.time.Duration;
import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otel.metric.export")
public class MetricExportProperties {

  @Nullable private Duration interval;

  @Nullable
  public Duration getInterval() {
    return interval;
  }

  public void setInterval(@Nullable Duration interval) {
    this.interval = interval;
  }
}
