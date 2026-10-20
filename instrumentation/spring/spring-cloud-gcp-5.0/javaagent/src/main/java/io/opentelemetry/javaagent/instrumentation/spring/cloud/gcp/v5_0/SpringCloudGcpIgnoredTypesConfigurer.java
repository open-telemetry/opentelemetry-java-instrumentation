/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class SpringCloudGcpIgnoredTypesConfigurer implements IgnoredTypesConfigurer {
  @Override
  public void configure(IgnoredTypesBuilder builder) {
    // the classes below are ignored by the AdditionalLibraryIgnoredTypesConfigurer in the full
    // agent, but that configurer is disabled during tests; the executors instrumentation would
    // still transform their Runnables / Callables / Futures, so ignore them here as well to keep
    // test behavior aligned with production
    builder
        // this module instruments the channel adapter, which would otherwise be ignored by the
        // additional library ignores for com.google.cloud.
        .allowClass(
            "com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter")
        .ignoreClass("com.google.api.core.")
        .ignoreClass("com.google.api.gax.")
        .ignoreClass("com.google.cloud.pubsub.v1.")
        // a Runnable wrapping message handling and ack
        .ignoreClass("org.springframework.messaging.support.MessageHandlingRunnable")
        .ignoreClass("org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration$");
  }
}
