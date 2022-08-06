/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v2_0.AbstractTracedWithSpan
import io.opentelemetry.instrumentation.rxjava.v2_0.extensionannotation.TracedWithSpan

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class RxJava2ExtensionWithSpanTest extends BaseRxJava2WithSpanTest {

  @Override
  AbstractTracedWithSpan newTraced() {
    return new TracedWithSpan()
  }
}
