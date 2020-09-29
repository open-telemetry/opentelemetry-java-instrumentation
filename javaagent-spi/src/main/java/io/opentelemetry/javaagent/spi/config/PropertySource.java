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

package io.opentelemetry.javaagent.spi.config;

import java.util.Map;

/**
 * A service provider that allows to override default OTel agent configuration. Properties returned
 * by implementations of this interface will be used after the following methods fail to find a
 * non-empty property value: system properties, environment variables, properties configuration
 * file.
 */
public interface PropertySource {
  /**
   * @return all properties whose default values are overridden by this property source. Key of the
   *     map is the propertyName (same as system property name, e.g. {@code otel.exporter}), value
   *     is the property value.
   */
  Map<String, String> getProperties();
}
