/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import io.opentelemetry.instrumentation.api.util.VirtualField;

public class TestSingletons {

  public static final VirtualField<Runnable, String> STRING =
      VirtualField.find(Runnable.class, String.class);

  private TestSingletons() {}
}
