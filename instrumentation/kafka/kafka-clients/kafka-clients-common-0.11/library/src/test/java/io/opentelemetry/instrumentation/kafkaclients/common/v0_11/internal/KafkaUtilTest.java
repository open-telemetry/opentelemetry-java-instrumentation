/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class KafkaUtilTest {

  @Test
  void parseBootstrapServers_withString() {
    // Test single server
    List<String> result = KafkaUtil.parseBootstrapServers("localhost:9092");
    assertThat(result).containsExactly("localhost:9092");

    // Test multiple servers
    result = KafkaUtil.parseBootstrapServers("localhost:9092,localhost:9093,localhost:9094");
    assertThat(result).containsExactly("localhost:9092", "localhost:9093", "localhost:9094");

    // Test with spaces
    result = KafkaUtil.parseBootstrapServers(" localhost:9092 , localhost:9093 , localhost:9094 ");
    assertThat(result).containsExactly("localhost:9092", "localhost:9093", "localhost:9094");

    // Test empty string
    result = KafkaUtil.parseBootstrapServers("");
    assertThat(result).isEmpty();

    // Test null string
    result = KafkaUtil.parseBootstrapServers((String) null);
    assertThat(result).isEmpty();
  }

  @Test
  void parseBootstrapServers_withObject() {
    // Test with String object
    List<String> result = KafkaUtil.parseBootstrapServers((Object) "localhost:9092,localhost:9093");
    assertThat(result).containsExactly("localhost:9092", "localhost:9093");

    // Test with List object
    List<String> inputList = Arrays.asList("localhost:9092", "localhost:9093");
    result = KafkaUtil.parseBootstrapServers(inputList);
    assertThat(result).containsExactly("localhost:9092", "localhost:9093");

    // Test with empty List
    result = KafkaUtil.parseBootstrapServers(Collections.emptyList());
    assertThat(result).isEmpty();

    // Test with null object
    result = KafkaUtil.parseBootstrapServers((Object) null);
    assertThat(result).isEmpty();

    // Test with unsupported object type
    result = KafkaUtil.parseBootstrapServers(123);
    assertThat(result).containsExactly("123");
  }

  @Test
  void parseBootstrapServers_edgeCases() {
    // Test with only commas
    List<String> result = KafkaUtil.parseBootstrapServers(",,,");
    assertThat(result).isEmpty();

    // Test with empty elements
    result = KafkaUtil.parseBootstrapServers("localhost:9092,,localhost:9093");
    assertThat(result).containsExactly("localhost:9092", "localhost:9093");

    // Test with whitespace only elements
    result = KafkaUtil.parseBootstrapServers("localhost:9092,   ,localhost:9093");
    assertThat(result).containsExactly("localhost:9092", "localhost:9093");
  }
}
