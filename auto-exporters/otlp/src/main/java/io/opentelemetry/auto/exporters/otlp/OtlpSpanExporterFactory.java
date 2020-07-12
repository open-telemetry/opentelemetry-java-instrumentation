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

package io.opentelemetry.auto.exporters.otlp;

import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.extensions.auto.config.Config;
import io.opentelemetry.sdk.extensions.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class OtlpSpanExporterFactory implements SpanExporterFactory {

  @Override
  public SpanExporter fromConfig(final Config config) {
    return OtlpGrpcSpanExporter.newBuilder()
        .readEnvironmentVariables()
        .readSystemProperties()
        .build();
  }
}
