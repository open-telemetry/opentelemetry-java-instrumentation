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

package io.opentelemetry.instrumentation.api.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CollectionParsers {
  private static final Logger log = LoggerFactory.getLogger(CollectionParsers.class);

  static List<String> parseList(String value) {
    String[] tokens = value.split(",", -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  static Map<String, String> parseMap(String value) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String token : value.split(",", -1)) {
      token = token.trim();
      String[] parts = token.split("=", -1);
      if (parts.length != 2) {
        log.warn("Invalid map config part, should be formatted key1=value1,key2=value2: {}", value);
        return Collections.emptyMap();
      }
      result.put(parts[0], parts[1]);
    }
    return Collections.unmodifiableMap(result);
  }

  private CollectionParsers() {}
}
