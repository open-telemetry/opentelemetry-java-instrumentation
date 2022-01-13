/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.cache.Cache;

final class Bridging {

  private static final Cache<String, AttributeKey<String>> tagsCache = Cache.bounded(1024);

  static Attributes tagsAsAttributes(Meter.Id id) {
    Iterable<Tag> tags = id.getTagsAsIterable();
    if (!tags.iterator().hasNext()) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (Tag tag : tags) {
      builder.put(tagsCache.computeIfAbsent(tag.getKey(), AttributeKey::stringKey), tag.getValue());
    }
    return builder.build();
  }

  static String description(Meter.Id id) {
    String description = id.getDescription();
    return description == null ? "" : description;
  }

  static String baseUnit(Meter.Id id) {
    String baseUnit = id.getBaseUnit();
    return baseUnit == null ? "1" : baseUnit;
  }

  static String statisticInstrumentName(Meter.Id id, Statistic statistic) {
    String prefix = id.getName() + ".";
    // use "total_time" instead of "total" to avoid clashing with Statistic.TOTAL
    String statisticStr =
        statistic == Statistic.TOTAL_TIME ? "total_time" : statistic.getTagValueRepresentation();
    return prefix + statisticStr;
  }

  private Bridging() {}
}
