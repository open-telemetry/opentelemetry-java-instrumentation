/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation.packageprivate;

import io.opentelemetry.instrumentation.annotations.WithSpan;

public abstract class PackagePrivateAnnotatedBase {

  @WithSpan
  String run() {
    return "base";
  }
}
