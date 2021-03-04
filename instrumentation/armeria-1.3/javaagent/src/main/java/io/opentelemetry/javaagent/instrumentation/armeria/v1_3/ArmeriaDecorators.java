/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTracing;
import java.util.function.Function;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class ArmeriaDecorators {

  private static final ArmeriaTracing TRACING = ArmeriaTracing.create(GlobalOpenTelemetry.get());

  public static final Function<? super HttpClient, ? extends HttpClient> CLIENT_DECORATOR =
      TRACING.newClientDecorator();

  public static final Function<? super HttpService, ? extends HttpService> SERVER_DECORATOR =
      TRACING.newServiceDecorator();
}
