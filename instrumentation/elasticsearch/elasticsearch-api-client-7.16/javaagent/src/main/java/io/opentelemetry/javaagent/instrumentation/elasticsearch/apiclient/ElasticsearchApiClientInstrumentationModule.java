/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ElasticsearchApiClientInstrumentationModule extends InstrumentationModule {
  public ElasticsearchApiClientInstrumentationModule() {
    super("elasticsearch-api-client-7.16", "elasticsearch");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RestClientTransportInstrumentation(), new RestClientHttpClientInstrumentation());
  }
}
