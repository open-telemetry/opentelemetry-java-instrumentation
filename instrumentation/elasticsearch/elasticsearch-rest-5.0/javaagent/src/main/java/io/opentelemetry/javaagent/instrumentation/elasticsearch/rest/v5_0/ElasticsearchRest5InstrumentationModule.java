/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ElasticsearchRest5InstrumentationModule extends InstrumentationModule {
  public ElasticsearchRest5InstrumentationModule() {
    super("elasticsearch-rest", "elasticsearch-rest-5.0", "elasticsearch");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestClientInstrumentation());
  }
}
