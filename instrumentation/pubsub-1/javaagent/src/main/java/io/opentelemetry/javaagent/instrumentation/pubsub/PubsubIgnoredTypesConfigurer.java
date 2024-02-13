package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class PubsubIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    builder.allowClass("com.google.cloud.pubsub.")
            .ignoreClass("com.google.api.")
            .allowClass("com.google.api.client.http.HttpRequest");
  }
}
