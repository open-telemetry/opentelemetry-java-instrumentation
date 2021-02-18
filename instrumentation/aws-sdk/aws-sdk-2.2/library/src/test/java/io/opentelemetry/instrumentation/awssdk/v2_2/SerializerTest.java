/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

public class SerializerTest {

  @Test
  public void shouldSerializeSimpleString() {
    // given
    // when
    String serialized = new Serializer().serialize("simpleString");
    // then
    assertThat(serialized).isEqualTo("simpleString");
  }

  @Test
  public void shouldSerializeSdkPojo() {
    // given
    SdkPojo sdkPojo =
        ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(2L).build();
    // when
    String serialized = new Serializer().serialize(sdkPojo);
    // then
    assertThat(serialized).isEqualTo("{\"ReadCapacityUnits\":1,\"WriteCapacityUnits\":2}");
  }

  @Test
  public void shouldSerializeCollection() {
    // given
    List<String> collection = Arrays.asList("one", "two", "three");
    // when
    String serialized = new Serializer().serialize(collection);
    // then
    assertThat(serialized).isEqualTo("[one,two,three]");
  }

  @Test
  public void shouldSerializeEmptyCollectionAsNull() {
    // given
    List<String> collection = Arrays.asList();
    // when
    String serialized = new Serializer().serialize(collection);
    // then
    assertThat(serialized).isNull();
  }

  @Test
  public void shouldSerializeMapAsKeyCollection() {
    // given
    Map<String, Object> map = new HashMap<>();
    map.put("uno", Long.valueOf(1));
    map.put("dos", new LinkedHashMap<>());
    map.put("tres", "cuatro");
    // when
    String serialized = new Serializer().serialize(map);
    // then
    assertThat(serialized).isEqualTo("[uno,dos,tres]");
  }
}
