package com.example.javaagent;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.ResourceProvider;

public class DemoResourceProvider extends ResourceProvider {
  @Override
  protected Attributes getAttributes() {
    return Attributes.builder().put("custom.resource", "demo").build();
  }
}
