package io.opentelemetry.javaagent.tooling.view;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import java.util.HashSet;
import java.util.Set;

public class ViewHelper {
  public static void registerView(SdkMeterProviderBuilder builder, String name, View view) {
    builder.registerView(InstrumentSelector.builder().setMeterName(name).build(), view);
  }

  public static View createView(Set<AttributeKey<?>> attributeKeys) {
    Set<String> keys = new HashSet<>(attributeKeys.size());
    attributeKeys.forEach(attributeKey -> keys.add(attributeKey.getKey()));
    return View.builder().setAttributeFilter(keys::contains).build();
  }
}
