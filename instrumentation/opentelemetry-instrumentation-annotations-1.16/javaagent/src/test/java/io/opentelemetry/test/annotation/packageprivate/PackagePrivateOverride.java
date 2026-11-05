/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation.packageprivate;

public class PackagePrivateOverride extends PackagePrivateAnnotatedBase {

  @Override
  String run() {
    return "package-private";
  }
}
