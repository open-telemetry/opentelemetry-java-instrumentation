/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Map;

final class SerializationTestUtil {

  /**
   * Tests that a configuration map can be serialized and that OpenTelemetry classes are replaced
   * with null during serialization (via writeReplace()).
   *
   * @param map the configuration map to serialize
   * @param configKey the key to check for in the deserialized map
   */
  static void testSerialize(Map<String, Object> map, String configKey)
      throws IOException, ClassNotFoundException {
    Object supplierValue = map.get(configKey);
    assertThat(supplierValue).isNotNull();

    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    try (ObjectOutputStream outputStream = new ObjectOutputStream(byteOutputStream)) {
      outputStream.writeObject(map);
    }

    class CustomObjectInputStream extends ObjectInputStream {
      CustomObjectInputStream(InputStream inputStream) throws IOException {
        super(inputStream);
      }

      @Override
      protected Class<?> resolveClass(ObjectStreamClass desc)
          throws IOException, ClassNotFoundException {
        if (desc.getName().startsWith("io.opentelemetry.")) {
          throw new IllegalStateException(
              "Serial form contains opentelemetry class " + desc.getName());
        }
        return super.resolveClass(desc);
      }
    }

    try (ObjectInputStream inputStream =
        new CustomObjectInputStream(new ByteArrayInputStream(byteOutputStream.toByteArray()))) {
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) inputStream.readObject();
      // After deserialization, the supplier should be null (replaced via writeReplace())
      assertThat(result.get(configKey)).isNull();
    }
  }

  private SerializationTestUtil() {}
}
