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

package io.opentelemetry.javaagent.exporters.gcp;

import com.google.auto.service.AutoService;
import com.google.cloud.opentelemetry.trace.TraceExporter;
import io.opentelemetry.javaagent.tooling.TracerInstaller;
import io.opentelemetry.javaagent.tooling.exporter.ExporterConfig;
import io.opentelemetry.javaagent.tooling.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(SpanExporterFactory.class)
public class TraceExporterFactory implements SpanExporterFactory {

  private static final Logger log = LoggerFactory.getLogger(TracerInstaller.class);

  @Override
  public SpanExporter fromConfig(ExporterConfig _config) {
    try {
      return TraceExporter.createWithDefaultConfiguration();
    } catch (IOException ex) {
      log.error("Unable to create Google Trace exporter.", ex);
      return null;
    }
  }
}
