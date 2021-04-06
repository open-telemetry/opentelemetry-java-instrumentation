/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.Dsl
import org.asynchttpclient.Request
import org.asynchttpclient.RequestBuilder
import org.asynchttpclient.Response
import org.asynchttpclient.uri.Uri
import spock.lang.AutoCleanup
import spock.lang.Shared

class AsyncHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @AutoCleanup
  @Shared
  def client = Dsl.asyncHttpClient()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    return client.executeRequest(buildRequest(method, uri, headers)).get().statusCode
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    client.executeRequest(buildRequest(method, uri, headers), new AsyncCompletionHandler<Void>() {
      @Override
      Void onCompleted(Response response) throws Exception {
        callback.accept(response.statusCode)
        return null
      }
    })
  }

  private static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    RequestBuilder requestBuilder = new RequestBuilder(method)
      .setUri(Uri.create(uri.toString()))
    headers.entrySet().each {
      requestBuilder.addHeader(it.key, it.value)
    }
    return requestBuilder.build()
  }

  //TODO see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2347
//  @Override
//  String userAgent() {
//    return "AHC"
//  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}


