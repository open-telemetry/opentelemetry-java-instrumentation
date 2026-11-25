/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import org.junit.jupiter.api.Test;

class DubboManualConfigurationTest {

  @Test
  void filtersAreNotActivatedAutomatically() {
    ExtensionLoader<Filter> filterLoader = ExtensionLoader.getExtensionLoader(Filter.class);
    URL url = new URL("dubbo", "localhost", 20880, "test");

    List<Filter> consumerFilters = filterLoader.getActivateExtension(url, "filter", "consumer");
    List<Filter> providerFilters = filterLoader.getActivateExtension(url, "filter", "provider");

    assertThat(consumerFilters)
        .noneMatch(filter -> filter.getClass().getName().contains("OpenTelemetry"));
    assertThat(providerFilters)
        .noneMatch(filter -> filter.getClass().getName().contains("OpenTelemetry"));
  }
}
