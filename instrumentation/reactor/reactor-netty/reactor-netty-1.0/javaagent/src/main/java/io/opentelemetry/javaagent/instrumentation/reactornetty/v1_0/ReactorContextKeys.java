/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

public final class ReactorContextKeys {

  public static final String CLIENT_PARENT_CONTEXT_KEY =
      ReactorContextKeys.class.getName() + ".client-parent-context";
  public static final String CLIENT_CONTEXT_KEY =
      ReactorContextKeys.class.getName() + ".client-context";

  private ReactorContextKeys() {}
}
