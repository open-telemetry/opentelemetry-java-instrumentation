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


import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import okhttp3.OkHttpClient
import spock.lang.Shared

import java.util.concurrent.TimeUnit

abstract class AbstractServerSmokeTest extends AbstractSmokeTest {

  @Shared
  int httpPort = PortUtils.randomOpenPort()


  protected OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    try {
      PortUtils.waitForPortToOpen(httpPort, 240, TimeUnit.SECONDS, testedProcess)
    } catch (e) {
      System.err.println(logfile.text)
      throw e
    }
  }

}
