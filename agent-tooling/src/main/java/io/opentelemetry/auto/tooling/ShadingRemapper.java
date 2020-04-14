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
package io.opentelemetry.auto.tooling;

import java.util.Map;
import java.util.TreeMap;
import net.bytebuddy.jar.asm.commons.Remapper;

public class ShadingRemapper extends Remapper {
  public static class Rule {
    private final String from;
    private final String to;

    public Rule(String from, String to) {
      // Strip prefix added to prevent the build-time relocation from changing the names
      if (from.startsWith("#")) {
        from = from.substring(1);
      }
      if (to.startsWith("#")) {
        to = to.substring(1);
      }
      this.from = from.replace('.', '/');
      this.to = to.replace('.', '/');
    }
  }

  public static Rule rule(final String from, final String to) {
    return new Rule(from, to);
  }

  private final TreeMap<String, String> map = new TreeMap<>();

  public ShadingRemapper(final Rule... rules) {
    for (final Rule rule : rules) {
      map.put(rule.from, rule.to);
    }
  }

  @Override
  public String map(final String internalName) {
    final Map.Entry<String, String> e = map.floorEntry(internalName);
    if (e != null && internalName.startsWith(e.getKey())) {
      return e.getValue() + internalName.substring(e.getKey().length());
    }
    return super.map(internalName);
  }
}
