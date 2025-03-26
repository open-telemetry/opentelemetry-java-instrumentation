/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

/**
 * Class that will be injected in target classloader with inline instrumentation and proxied with
 * indy instrumentation
 */
public class TestHelperClass {

  public TestHelperClass() {}
}
