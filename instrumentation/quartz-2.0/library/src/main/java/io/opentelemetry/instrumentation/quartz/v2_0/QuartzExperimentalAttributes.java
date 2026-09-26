/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Experimental (non-semconv) attributes captured only when experimental attributes are enabled.
 * Their names and shapes may change until Quartz scheduler instrumentation stabilizes.
 */
final class QuartzExperimentalAttributes {

  static final AttributeKey<String> SCHEDULER_NAME = stringKey("quartz.scheduler.name");

  private QuartzExperimentalAttributes() {}
}
