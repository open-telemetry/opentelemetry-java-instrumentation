/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheHttpClientInstrumentationModule extends InstrumentationModule {

  public ApacheHttpClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "apache-httpclient-5.0" : "apache-httpclient",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"apache-httpclient"}
            : new String[] {"apache-httpclient-5.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ApacheHttpClientInstrumentation(), new ApacheHttpAsyncClientInstrumentation());
  }
}
