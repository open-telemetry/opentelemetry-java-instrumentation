/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringCloudGcpInstrumentationModule extends InstrumentationModule {
  public SpringCloudGcpInstrumentationModule() {
    super("spring-cloud-gcp", "spring-cloud-gcp-5.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.0.0 when the project moved to com.google.cloud groupId
    return hasClassesNamed(
        "com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new PubSubInboundChannelAdapterInstrumentation());
  }
}
