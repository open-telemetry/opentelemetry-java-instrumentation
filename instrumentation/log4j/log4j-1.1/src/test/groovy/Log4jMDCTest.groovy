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
import io.opentelemetry.auto.test.log.injection.LogContextInjectionTestBase
import org.apache.log4j.MDC
import org.junit.Ignore
import spock.lang.Requires

/**
 It looks like log4j1 is broken for any java version that doesn't have '.' in version number
 - it thinks it runs on ancient version. For example this happens for java13.
 See {@link org.apache.log4j.helpers.Loader}.
 */
// FIXME this instrumentation relied on scope listener
@Ignore
@Requires({ System.getProperty("java.version").contains(".") })
class Log4jMDCTest extends LogContextInjectionTestBase {

  @Override
  def put(String key, Object value) {
    return MDC.put(key, value)
  }

  @Override
  def get(String key) {
    return MDC.get(key)
  }
}
