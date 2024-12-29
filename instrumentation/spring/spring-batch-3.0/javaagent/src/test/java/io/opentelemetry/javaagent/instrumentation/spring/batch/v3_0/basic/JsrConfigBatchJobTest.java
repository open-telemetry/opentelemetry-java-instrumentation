/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.basic;

import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JavaxBatchConfigRunner;
import org.junit.jupiter.api.extension.RegisterExtension;

class JsrConfigBatchJobTest extends SpringBatchTest {
  @Override
  protected boolean hasPartitionManagerStep() {
    return false;
  }

  @RegisterExtension static final JavaxBatchConfigRunner runner = new JavaxBatchConfigRunner();

  public JsrConfigBatchJobTest() {
    super(runner);
  }
}
