/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.amazonaws.services.lambda.runtime.serialization.events.LambdaEventSerializers;
import com.amazonaws.services.lambda.runtime.serialization.factories.JacksonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SerializationUtil {

  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private static final ClassValue<PojoSerializer<?>> serializerCache =
      new ClassValue<PojoSerializer<?>>() {
        @Override
        protected PojoSerializer<?> computeValue(Class<?> type) {
          return createSerializer(type);
        }
      };

  private static <T> PojoSerializer<T> createSerializer(Class<T> clazz) {
    try {
      if (LambdaEventSerializers.isLambdaSupportedEvent(clazz.getName())) {
        return LambdaEventSerializers.serializerFor(clazz, clazz.getClassLoader());
      }
      return JacksonFactory.getInstance().getSerializer(clazz);
    } catch (NoClassDefFoundError e) {
      // For "java8" runtime, "aws-lambda-java-serialization" library
      // is not available in the classpath by default.
      // So fall back to object mapper based legacy serialization.
      return new ObjectMapperPojoSerializer<T>(clazz);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> PojoSerializer<T> getSerializer(Class<T> clazz) {
    return (PojoSerializer<T>) serializerCache.get(clazz);
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    PojoSerializer<T> serializer = getSerializer(clazz);
    return serializer.fromJson(json);
  }

  public static <T> T fromJson(InputStream inputStream, Class<T> clazz) {
    PojoSerializer<T> serializer = getSerializer(clazz);
    return serializer.fromJson(inputStream);
  }

  @SuppressWarnings("unchecked")
  public static <T> void toJson(OutputStream outputStream, T obj) {
    if (obj != null) {
      PojoSerializer<T> serializer = getSerializer((Class<T>) obj.getClass());
      serializer.toJson(obj, outputStream);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> String toJson(T obj) {
    if (obj == null) {
      return null;
    }
    PojoSerializer<T> serializer = getSerializer((Class<T>) obj.getClass());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
    serializer.toJson(obj, outputStream);
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }

  public static <T> byte[] toJsonData(T obj) {
    if (obj == null) {
      return new byte[] {};
    }
    ByteArrayOutputStream os = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
    SerializationUtil.toJson(os, obj);
    return os.toByteArray();
  }

  private static class ObjectMapperPojoSerializer<T> implements PojoSerializer<T> {

    private final ObjectMapper objectMapper =
        new ObjectMapper()
            .registerModule(new CustomJodaModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Class<T> clazz;

    ObjectMapperPojoSerializer(Class<T> clazz) {
      this.clazz = clazz;
    }

    @Override
    public T fromJson(InputStream input) {
      try {
        return objectMapper.readValue(input, clazz);
      } catch (IOException e) {
        throw new IllegalStateException("Could not deserialize from JSON input stream.", e);
      }
    }

    @Override
    public T fromJson(String input) {
      try {
        return objectMapper.readValue(input, clazz);
      } catch (IOException e) {
        throw new IllegalStateException("Could not deserialize from JSON string.", e);
      }
    }

    @Override
    public void toJson(T value, OutputStream output) {
      try {
        objectMapper.writeValue(output, value);
      } catch (IOException e) {
        throw new IllegalStateException("Could not serialize to JSON.", e);
      }
    }
  }

  private SerializationUtil() {}
}
