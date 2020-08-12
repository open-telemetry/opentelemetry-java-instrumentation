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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import io.opentelemetry.auto.test.base.HttpClientTest
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig
import spock.lang.Shared

abstract class PlayWSClientTestBase extends HttpClientTest {
  @Shared
  ActorSystem system

  @Shared
  AsyncHttpClient asyncHttpClient

  @Shared
  ActorMaterializer materializer

  def setupSpec() {
    String name = "play-ws"
    system = ActorSystem.create(name)
    ActorMaterializerSettings settings = ActorMaterializerSettings.create(system)
    materializer = ActorMaterializer.create(settings, system, name)

    AsyncHttpClientConfig asyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxRequestRetry(0)
        .setShutdownQuietPeriod(0)
        .setShutdownTimeout(0)
        .build()

    asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
  }

  def cleanupSpec() {
    system?.terminate()
  }

  String expectedOperationName() {
    return "play-ws.request"
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }

  @Override
  boolean testCallbackWithParent() {
    return false
  }

  @Override
  boolean testRemoteConnection() {
    // TODO(anuraaga): Timeouts contain ConnectException in client span instead of TimeoutException
    return false
  }
}
