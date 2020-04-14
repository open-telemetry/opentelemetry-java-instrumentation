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
package jvmbootstraptest;

public class LogLevelChecker {
  // returns an exception if logs are not in DEBUG
  public static void main(final String[] args) {

    final String str =
        System.getProperty("io.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel");

    if ((str == null) || (str != null && !str.equalsIgnoreCase("debug"))) {
      throw new RuntimeException("debug mode not set");
    }
  }
}
