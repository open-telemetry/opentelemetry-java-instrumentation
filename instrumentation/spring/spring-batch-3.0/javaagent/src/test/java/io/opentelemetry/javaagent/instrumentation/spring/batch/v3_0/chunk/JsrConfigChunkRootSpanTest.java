/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.chunk;

import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JavaxBatchConfigRunner;
import org.junit.jupiter.api.extension.RegisterExtension;

class JsrConfigChunkRootSpanTest extends ChunkRootSpanTest {

  @RegisterExtension static final JavaxBatchConfigRunner runner = new JavaxBatchConfigRunner();

  public JsrConfigChunkRootSpanTest() {
    super(runner);
  }
}
