/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.util.Arrays.asList;

class SofaRpcTest extends AbstractSofaRpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final SofaRpcTelemetry telemetry =
      SofaRpcTelemetry.create(testing.getOpenTelemetry());

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean hasPeerService() {
    return false;
  }

  @Override
  protected List<Filter> clientFilters() {
    return asList(telemetry.newClientFilter());
  }

  @Override
  protected List<Filter> serverFilters() {
    return asList(telemetry.newServerFilter());
  }
}
