/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTracing;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTracingBuilder;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class ArmeriaDecorators {
  private static final Logger log = LoggerFactory.getLogger(ArmeriaDecorators.class);

  public static final Function<? super HttpClient, ? extends HttpClient> CLIENT_DECORATOR;

  public static final Function<? super HttpService, ? extends HttpService> SERVER_DECORATOR;

  static {
    ArmeriaTracingBuilder builder = ArmeriaTracing.newBuilder(GlobalOpenTelemetry.get());

    Constructor<? extends NetAttributesExtractor<RequestContext, RequestLog>> constructor = null;
    try {
      Class<? extends NetAttributesExtractor<RequestContext, RequestLog>>
          netAttributesExtractorClass =
              (Class<? extends NetAttributesExtractor<RequestContext, RequestLog>>)
                  Class.forName(
                      "io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaNetAttributesExtractor");
      constructor = netAttributesExtractorClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      NetAttributesExtractor<RequestContext, RequestLog> netAttributesExtractor =
          constructor.newInstance();
      builder.addAttributeExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor));
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      log.warn("Could not add PeerServiceAttributesExtractor to armeria", e);
    } finally {
      if (constructor != null) {
        constructor.setAccessible(false);
      }
    }

    ArmeriaTracing tracing = builder.build();
    CLIENT_DECORATOR = tracing.newClientDecorator();
    SERVER_DECORATOR = tracing.newServiceDecorator();
  }
}
