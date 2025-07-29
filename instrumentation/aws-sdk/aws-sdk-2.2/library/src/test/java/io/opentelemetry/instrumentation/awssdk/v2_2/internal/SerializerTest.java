/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

class SerializerTest {

  @Test
  void shouldSerializeSimpleString() {
    // given
    // when
    String serialized = new Serializer().serialize("simpleString");
    // then
    assertThat(serialized).isEqualTo("simpleString");
  }

  @Test
  void shouldSerializeSdkPojo() {
    // given
    SdkPojo sdkPojo =
        ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(2L).build();
    // when
    String serialized = new Serializer().serialize(sdkPojo);
    // then
    assertThat(serialized).isEqualTo("{\"ReadCapacityUnits\":1,\"WriteCapacityUnits\":2}");
  }

  @Test
  void shouldSerializeCollection() {
    // given
    List<String> collection = Arrays.asList("one", "two", "three");
    // when
    String serialized = new Serializer().serialize(collection);
    // then
    assertThat(serialized).isEqualTo("[one,two,three]");
  }

  @Test
  void shouldSerializeEmptyCollectionAsNull() {
    // given
    List<String> collection = Collections.emptyList();
    // when
    String serialized = new Serializer().serialize(collection);
    // then
    assertThat(serialized).isNull();
  }

  @Test
  void shouldSerializeMapAsKeyCollection() {
    // given
    Map<String, Object> map = new HashMap<>();
    map.put("uno", 1L);
    map.put("dos", new LinkedHashMap<>());
    map.put("tres", "cuatro");
    // when
    String serialized = new Serializer().serialize(map);
    // then
    assertThat(serialized).isEqualTo("[uno,dos,tres]");
  }
}
