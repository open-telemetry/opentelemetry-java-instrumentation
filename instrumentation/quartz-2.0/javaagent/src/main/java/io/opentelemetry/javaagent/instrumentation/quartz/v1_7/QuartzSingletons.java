/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v1_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.quartz.v2_0.QuartzTracing;

public final class QuartzSingletons {

  public static final QuartzTracing TRACING = QuartzTracing.create(GlobalOpenTelemetry.get());

  private QuartzSingletons() {}
}
