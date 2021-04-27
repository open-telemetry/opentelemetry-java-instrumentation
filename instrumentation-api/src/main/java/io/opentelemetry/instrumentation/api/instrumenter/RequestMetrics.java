/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

public interface RequestMetrics {

  Context start(Context context, Attributes requestAttributes);

  void end(Context context, Attributes responseAttributes);
}
