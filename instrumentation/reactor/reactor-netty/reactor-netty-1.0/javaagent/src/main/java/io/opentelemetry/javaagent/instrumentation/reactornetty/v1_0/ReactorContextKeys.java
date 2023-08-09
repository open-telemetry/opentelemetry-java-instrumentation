/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

public final class ReactorContextKeys {

  public static final String CONTEXTS_HOLDER_KEY =
      ReactorContextKeys.class.getName() + ".contexts-holder";

  private ReactorContextKeys() {}
}
