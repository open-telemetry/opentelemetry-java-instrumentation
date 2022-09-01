/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class Bridging {

  private static final ConcurrentMap<String, String> descriptionsCache = new ConcurrentHashMap<>();

  static Attributes tagsAsAttributes(Meter.Id id, NamingConvention namingConvention) {
    Iterable<Tag> tags = id.getTagsAsIterable();
    if (!tags.iterator().hasNext()) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (Tag tag : tags) {
      String tagKey = namingConvention.tagKey(tag.getKey());
      String tagValue = namingConvention.tagValue(tag.getValue());
      builder.put(tagKey, tagValue);
    }
    return builder.build();
  }

  static String name(Meter.Id id, NamingConvention namingConvention) {
    return namingConvention.name(id.getName(), id.getType(), id.getBaseUnit());
  }

  static String description(Meter.Id id) {
    return descriptionsCache.computeIfAbsent(
        id.getName(),
        n -> {
          String description = id.getDescription();
          return description != null ? description : "";
        });
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
    return namingConvention.name(prefix + statisticStr, id.getType(), id.getBaseUnit());
  }

  private Bridging() {}
}
