/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import static java.util.Collections.singletonList;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

// Setting native headers is required by some protocols, e.g. STOMP
// see https://github.com/spring-cloud/spring-cloud-sleuth/issues/716 for more details
// Native headers logic inspired by
// https://github.com/spring-cloud/spring-cloud-sleuth/blob/main/spring-cloud-sleuth-instrumentation/src/main/java/org/springframework/cloud/sleuth/instrument/messaging/MessageHeaderPropagatorSetter.java
enum MessageHeadersSetter implements TextMapSetter<MessageHeaderAccessor> {
  INSTANCE;

  @Override
  public void set(MessageHeaderAccessor carrier, String key, String value) {
    carrier.setHeader(key, value);
    setNativeHeader(carrier, key, value);
  }

  private static void setNativeHeader(MessageHeaderAccessor carrier, String key, String value) {
    Object nativeMap = carrier.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
    if (nativeMap instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, List<String>> map = ((Map<String, List<String>>) nativeMap);
      map.put(key, singletonList(value));
    }
  }
}
