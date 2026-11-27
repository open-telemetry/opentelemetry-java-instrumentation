/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation.packageprivate;

public class PublicOverride extends PackagePrivateAnnotatedBase {

  @Override
  public String run() {
    return "public";
  }
}
