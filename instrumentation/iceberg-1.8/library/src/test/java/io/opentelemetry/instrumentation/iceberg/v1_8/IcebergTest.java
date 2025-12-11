/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.iceberg.TableScan;
import org.junit.jupiter.api.extension.RegisterExtension;

class IcebergTest extends AbstractIcebergTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected TableScan configure(TableScan tableScan) {
    OpenTelemetry openTelemetry = testing.getOpenTelemetry();
    IcebergTelemetry icebergTelemetry = IcebergTelemetry.create(openTelemetry);
    return icebergTelemetry.wrapScan(tableScan);
  }
}
