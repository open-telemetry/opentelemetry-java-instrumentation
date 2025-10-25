/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;

@SuppressWarnings("unused")
public class TestAdvice {

  @AssignReturned.ToReturned
  @Advice.OnMethodExit
  public static boolean returnTrue() {
    return true;
  }
}
