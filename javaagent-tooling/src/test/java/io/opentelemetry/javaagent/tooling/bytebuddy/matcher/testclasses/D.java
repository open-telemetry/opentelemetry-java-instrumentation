/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses;

@SuppressWarnings("ClassNamedLikeTypeParameter")
public interface D extends A, B, C {
  @Trace
  void d();
}
