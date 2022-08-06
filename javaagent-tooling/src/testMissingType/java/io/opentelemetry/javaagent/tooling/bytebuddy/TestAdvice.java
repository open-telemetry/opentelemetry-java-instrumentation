/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import net.bytebuddy.asm.Advice;

@SuppressWarnings({"PrivateConstructorForUtilityClass", "unused"})
public class TestAdvice {

  @Advice.OnMethodExit
  public static void returnTrue(@Advice.Return(readOnly = false) boolean returnVal) {
    returnVal = true;
  }
}
