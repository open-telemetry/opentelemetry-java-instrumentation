/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses;

public class PackagePrivateBase {

  @Trace
  void run() {}
}
