package io.opentelemetry.instrumentation.kafkaclients;

import java.util.Set;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import javax.annotation.Nullable;

/**
 * A value class collecting the identifying fields of a kafka {@link MetricName}.
 *
 * <p>Note: {@link #description} is included in {@link #toString()} but omitted from {@link
 * #equals(Object)} and {@link #hashCode()}.
 */
class KafkaMetricId {

  private final String name;
  private final String group;
  private final String description;
  private final Set<String> tagKeys;

  private KafkaMetricId(String name, String group, String description, Set<String> tagKeys) {
    this.name = name;
    this.group = group;
    this.description = description;
    this.tagKeys = tagKeys;
  }

  static KafkaMetricId create(KafkaMetric kafkaMetric) {
    return new KafkaMetricId(
        kafkaMetric.metricName().name(),
        kafkaMetric.metricName().group(),
        kafkaMetric.metricName().description(),
        kafkaMetric.metricName().tags().keySet());
  }

  static KafkaMetricId create(String name, String group, Set<String> tagKeys) {
    return new KafkaMetricId(name, group, "", tagKeys);
  }

  String getName() {
    return name;
  }

  String getGroup() {
    return group;
  }

  String getDescription() {
    return description;
  }

  Set<String> getTagKeys() {
    return tagKeys;
  }

  @Override
  public String toString() {
    return "KafkaMetricId{"
        + "name="
        + name
        + ", "
        + "group="
        + group
        + ", "
        + "description="
        + description
        + ", "
        + "tagKeys="
        + tagKeys
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KafkaMetricId) {
      KafkaMetricId that = (KafkaMetricId) o;
      // Omit description from equality
      return this.name.equals(that.getName())
          && this.group.equals(that.getGroup())
          && this.tagKeys.equals(that.getTagKeys());
    }
    return false;
  }

  @Override
  public int hashCode() {
    // Omit description from hashcode
    int hash = 1;
    hash *= 1000003;
    hash ^= name.hashCode();
    hash *= 1000003;
    hash ^= group.hashCode();
    hash *= 1000003;
    hash ^= tagKeys.hashCode();
    return hash;
  }
}
