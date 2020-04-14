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
package io.opentelemetry.smoketest

class SpanCounter {

  long expiration

  final Map<String, Integer> targets

  int totalTargets = 0

  final Reader reader

  final Map<String, Integer> counters

  SpanCounter(File file, Map<String, Integer> targets, long timeout) {
    reader = file.newReader()
    reader.skip(file.length())

    expiration = System.currentTimeMillis() + timeout
    this.targets = targets
    counters = new HashMap<>(targets.size())
    targets.keySet().each({
      totalTargets += targets[it]
      counters[it] = 0
    })
  }

  Map<String, Integer> countSpans() {
    try {
      def line
      while (System.currentTimeMillis() < expiration) {
        line = reader.readLine()
        if (line) {
          for (def key : counters.keySet()) {
            if (line.startsWith(key)) {
              counters[key]++
              if (--totalTargets == 0) {
                // We hit our total target. We may or may not have gotten the right
                // number for each tag, but we're letting the caller sort that out!
                return counters
              }
            }
          }
        } else {
          Thread.sleep(10)
        }
      }
    }
    finally {
      reader?.close()
    }
    return counters
  }
}

