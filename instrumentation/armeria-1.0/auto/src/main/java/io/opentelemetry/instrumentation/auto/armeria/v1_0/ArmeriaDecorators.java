/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.armeria.v1_0;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient;
import io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService;
import java.util.function.Function;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class ArmeriaDecorators {

  public static final Function<? super HttpClient, ? extends HttpClient> CLIENT_DECORATOR =
      OpenTelemetryClient.newDecorator();

  public static final Function<? super HttpService, ? extends HttpService> SERVER_DECORATOR =
      OpenTelemetryService.newDecorator();
}
