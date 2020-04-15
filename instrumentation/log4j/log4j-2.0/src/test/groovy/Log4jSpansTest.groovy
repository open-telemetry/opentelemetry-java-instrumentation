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
import io.opentelemetry.auto.test.log.events.LogEventsTestBase
import org.apache.logging.log4j.LogManager

class Log4jSpansTest extends LogEventsTestBase {

  static {
    // need to initialize logger before tests to flush out init warning message:
    // "Unable to instantiate org.fusesource.jansi.WindowsAnsiOutputStream"
    LogManager.getLogger(Log4jSpansTest)
  }

  @Override
  Object createLogger(String name) {
    LogManager.getLogger(name)
  }
}
