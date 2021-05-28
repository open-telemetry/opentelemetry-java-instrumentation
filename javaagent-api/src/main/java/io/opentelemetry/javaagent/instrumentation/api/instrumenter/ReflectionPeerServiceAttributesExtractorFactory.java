/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReflectionPeerServiceAttributesExtractorFactory {
  private static final Logger log =
      LoggerFactory.getLogger(ReflectionPeerServiceAttributesExtractorFactory.class);

  @Nullable
  static <REQUEST, RESPONSE> PeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
      String netAttributesImplClassName) {
    Constructor<? extends NetAttributesExtractor<REQUEST, RESPONSE>> constructor = null;
    try {
      Class<? extends NetAttributesExtractor<REQUEST, RESPONSE>> netAttributesExtractorClass =
          (Class<? extends NetAttributesExtractor<REQUEST, RESPONSE>>)
              Class.forName(netAttributesImplClassName);
      constructor = netAttributesExtractorClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      NetAttributesExtractor<REQUEST, RESPONSE> netAttributesExtractor = constructor.newInstance();
      return PeerServiceAttributesExtractor.create(netAttributesExtractor);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      log.warn(
          "Could not add PeerServiceAttributesExtractor wrapping {}",
          netAttributesImplClassName,
          e);
      return null;
    } finally {
      if (constructor != null) {
        constructor.setAccessible(false);
      }
    }
  }

  private ReflectionPeerServiceAttributesExtractorFactory() {}
}
