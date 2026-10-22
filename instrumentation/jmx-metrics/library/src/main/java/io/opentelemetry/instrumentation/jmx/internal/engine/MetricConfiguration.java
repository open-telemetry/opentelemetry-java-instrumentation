/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A class responsible for maintaining the current configuration for JMX metrics to be collected.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class MetricConfiguration {

  private final Collection<MetricDef> currentSet = new ArrayList<>();
  private final Set<MetricDef> unstableSet = Collections.newSetFromMap(new IdentityHashMap<>());
  private final IdentityHashMap<MetricDef, String> schemaUrls = new IdentityHashMap<>();

  public MetricConfiguration() {}

  public boolean isEmpty() {
    return currentSet.isEmpty();
  }

  public void addMetricDef(MetricDef def) {
    addMetricDef(def, false, null);
  }

  public void addMetricDef(MetricDef def, boolean unstable, @Nullable String schemaUrl) {
    currentSet.add(def);
    if (unstable) {
      unstableSet.add(def);
    }
    if (schemaUrl != null) {
      schemaUrls.put(def, schemaUrl);
    }
  }

  boolean isUnstable(MetricDef def) {
    return unstableSet.contains(def);
  }

  @Nullable
  String getSchemaUrl(MetricDef def) {
    return schemaUrls.get(def);
  }

  Collection<MetricDef> getMetricDefs() {
    return currentSet;
  }
}
