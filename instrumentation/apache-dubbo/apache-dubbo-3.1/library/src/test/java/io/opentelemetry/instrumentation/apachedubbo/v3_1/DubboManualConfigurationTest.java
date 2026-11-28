/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v3_1.internal.OpenTelemetryServerFilter;
import java.util.List;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DubboManualConfigurationTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "testSecond,testFirst",
        "testSecond,default,testFirst",
        "-default,testSecond,testFirst",
        "testSecond,-token,default,testFirst"
      })
  void serverTracingPrecedesDefaultsWithoutChangingOtherFilters(String filters) {
    FrameworkModel framework = new FrameworkModel();
    try {
      ExtensionLoader<Filter> loader =
          framework.newApplication().getDefaultModule().getExtensionLoader(Filter.class);
      URL url = new URL("dubbo", "localhost", 20880, "test").addParameter("token", "test-token");
      List<Filter> original =
          loader.getActivateExtension(
              filters == null ? url : url.addParameter("service.filter", filters),
              "service.filter",
              "provider");
      ServiceConfig<?> service = new ServiceConfig<>();
      service.setFilter(filters);
      DubboTelemetry telemetry = DubboTelemetry.create(OpenTelemetry.noop());
      telemetry.instrument(service);
      String instrumentedFilters = service.getFilter();
      telemetry.instrument(service);
      assertThat(service.getFilter()).isEqualTo(instrumentedFilters);
      List<Filter> instrumented =
          loader.getActivateExtension(
              url.addParameter("service.filter", service.getFilter()),
              "service.filter",
              "provider");
      Filter tracing = loader.getExtension(OpenTelemetryServerFilter.NAME);
      assertThat(instrumented).containsOnlyOnce(tracing);
      Filter token = loader.getExtension("token");
      if (original.contains(token)) {
        assertThat(instrumented.indexOf(tracing)).isLessThan(instrumented.indexOf(token));
      }
      assertThat(instrumented)
          .filteredOn(filter -> filter != tracing)
          .containsExactlyElementsOf(original);
    } finally {
      framework.destroy();
    }
  }

  @Test
  void filtersAreNotActivatedAutomatically() {
    FrameworkModel framework = new FrameworkModel();
    URL url = new URL("dubbo", "localhost", 20880, "test");

    try {
      ExtensionLoader<Filter> loader =
          framework.newApplication().getDefaultModule().getExtensionLoader(Filter.class);
      List<Filter> consumerFilters =
          loader.getActivateExtension(url, "reference.filter", "consumer");
      List<Filter> providerFilters = loader.getActivateExtension(url, "service.filter", "provider");

      assertThat(consumerFilters)
          .noneMatch(filter -> filter.getClass().getName().contains("OpenTelemetry"));
      assertThat(providerFilters)
          .noneMatch(filter -> filter.getClass().getName().contains("OpenTelemetry"));
    } finally {
      framework.destroy();
    }
  }
}
