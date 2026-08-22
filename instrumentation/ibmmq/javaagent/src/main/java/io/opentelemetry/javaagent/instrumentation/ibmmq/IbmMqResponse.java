/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class IbmMqResponse {

  static IbmMqResponse create() {
    return new AutoValue_IbmMqResponse();
  }
}
