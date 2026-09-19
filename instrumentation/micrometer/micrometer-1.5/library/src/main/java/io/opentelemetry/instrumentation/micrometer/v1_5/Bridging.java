/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class Bridging {

  private final ConcurrentMap<String, String> descriptionsCache = new ConcurrentHashMap<>();
  private final boolean v3Preview;

  Bridging(boolean v3Preview) {
    this.v3Preview = v3Preview;
  }

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

  // Micrometer allows every set of tags to carry its own description, while in OpenTelemetry the
  // description is one of an instrument's identifying fields. Bridging the descriptions through
  // unchanged would turn a single Micrometer metric into conflicting OpenTelemetry instruments that
  // share a name, which the SDK exports as separate metric streams instead of aggregating into one.
  // So the first description seen for an instrument name wins, which is also what Micrometer's own
  // PrometheusMeterRegistry does. Callers must pass the name the instrument is actually emitted
  // under, including any suffix such as ".max", since that is the name a conflict would occur on.
  String description(String instrumentName, Meter.Id id) {
    return descriptionsCache.computeIfAbsent(
        instrumentName,
        n -> {
          String description = id.getDescription();
          return description != null ? description : "";
        });
  }

  static String baseUnit(Meter.Id id) {
    String baseUnit = id.getBaseUnit();
    return baseUnit == null ? "" : baseUnit;
  }

  String statisticInstrumentName(
      Meter.Id id, Statistic statistic, NamingConvention namingConvention) {
    // use "total_time" instead of "total" to avoid clashing with Statistic.TOTAL
    String statisticStr =
        statistic == Statistic.TOTAL_TIME ? "total_time" : statistic.getTagValueRepresentation();
    if (v3Preview) {
      return name(id, namingConvention) + "." + statisticStr;
    }
    return namingConvention.name(id.getName() + "." + statisticStr, id.getType(), id.getBaseUnit());
  }
}
