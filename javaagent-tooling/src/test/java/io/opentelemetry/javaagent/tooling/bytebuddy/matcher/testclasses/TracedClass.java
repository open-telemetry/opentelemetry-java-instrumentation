/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses;

public class TracedClass extends UntracedClass {
  @Trace
  @Override
  public void g() {}

  @Trace
  @Override
  public void f() {}

  @Trace
  @Override
  public void e() {}

  @Trace
  @Override
  public void d() {}

  @Trace
  @Override
  public void c() {}

  @Trace
  @Override
  public void b() {}

  @Trace
  @Override
  public void a() {}
}
