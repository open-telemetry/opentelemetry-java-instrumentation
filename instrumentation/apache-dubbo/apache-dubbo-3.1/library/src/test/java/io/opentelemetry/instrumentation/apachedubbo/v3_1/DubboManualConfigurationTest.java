/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.Test;

class DubboManualConfigurationTest {

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
