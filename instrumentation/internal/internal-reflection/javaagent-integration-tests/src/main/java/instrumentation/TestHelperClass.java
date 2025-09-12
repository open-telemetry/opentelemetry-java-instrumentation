/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import io.opentelemetry.instrumentation.api.util.VirtualField;

/**
 * Class that will be injected in target classloader with inline instrumentation and proxied with
 * indy instrumentation
 */
public class TestHelperClass {

  // virtual field needs to be in an helper class for indy instrumentation
  public static final VirtualField<Runnable, String> VIRTUAL_FIELD =
      VirtualField.find(Runnable.class, String.class);

  public TestHelperClass() {}
}
