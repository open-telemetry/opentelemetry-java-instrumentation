/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.cache.Cache;

final class Bridging {

  private static final Cache<String, AttributeKey<String>> tagsCache = Cache.bounded(1024);

  static Attributes tagsAsAttributes(Meter.Id id, NamingConvention namingConvention) {
    Iterable<Tag> tags = id.getTagsAsIterable();
    if (!tags.iterator().hasNext()) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (Tag tag : tags) {
      String tagKey = namingConvention.tagKey(tag.getKey());
      String tagValue = namingConvention.tagValue(tag.getValue());
      builder.put(tagsCache.computeIfAbsent(tagKey, AttributeKey::stringKey), tagValue);
    }
    return builder.build();
  }

  static String name(Meter.Id id, NamingConvention namingConvention) {
    return name(id.getName(), id, namingConvention);
  }

  private static String name(String name, Meter.Id id, NamingConvention namingConvention) {
    return namingConvention.name(name, id.getType(), id.getBaseUnit());
  }

  static String description(Meter.Id id) {
    String description = id.getDescription();
    return description == null ? "" : description;
  }

  static String baseUnit(Meter.Id id) {
    String baseUnit = id.getBaseUnit();
    return baseUnit == null ? "1" : baseUnit;
  }

  static String statisticInstrumentName(
      Meter.Id id, Statistic statistic, NamingConvention namingConvention) {
    String prefix = id.getName() + ".";
    // use "total_time" instead of "total" to avoid clashing with Statistic.TOTAL
    String statisticStr =
        statistic == Statistic.TOTAL_TIME ? "total_time" : statistic.getTagValueRepresentation();
    return name(prefix + statisticStr, id, namingConvention);
  }

  private Bridging() {}
}
