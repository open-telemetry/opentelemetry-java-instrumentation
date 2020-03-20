/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator

import io.opentelemetry.auto.config.Config
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Status
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride

class HttpClientDecoratorTest extends ClientDecoratorTest {

  @Shared
  def testUrl = new URI("http://myhost/somepath")

  def span = Mock(Span)

  def "test onRequest"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      decorator.onRequest(span, req)
    }

    then:
    if (req) {
      1 * span.setAttribute(Tags.HTTP_METHOD, req.method)
      1 * span.setAttribute(Tags.HTTP_URL, "$req.url")
      1 * span.setAttribute(MoreTags.NET_PEER_NAME, req.host)
      1 * span.setAttribute(MoreTags.NET_PEER_PORT, req.port)
      if (renameService) {
        1 * span.setAttribute(MoreTags.SERVICE_NAME, req.host)
      }
    }
    0 * _

    where:
    renameService | req
    false         | null
    true          | null
    false         | [method: "test-method", url: testUrl, host: "test-host", port: 555]
    true          | [method: "test-method", url: testUrl, host: "test-host", port: 555]
  }

  def "test url handling for #url"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_TAG_QUERY_STRING, "$tagQueryString") {
      decorator.onRequest(span, req)
    }

    then:
    if (expectedUrl) {
      1 * span.setAttribute(Tags.HTTP_URL, expectedUrl)
    }
    if (expectedUrl && tagQueryString) {
      1 * span.setAttribute(MoreTags.HTTP_QUERY, expectedQuery)
      1 * span.setAttribute(MoreTags.HTTP_FRAGMENT, expectedFragment)
    }
    1 * span.setAttribute(Tags.HTTP_METHOD, null)
    1 * span.setAttribute(MoreTags.NET_PEER_NAME, null)
    0 * _

    where:
    tagQueryString | url                                                   | expectedUrl                                     | expectedQuery      | expectedFragment
    false          | null                                                  | null                                            | null               | null
    false          | ""                                                    | "/"                                             | ""                 | null
    false          | "/path?query"                                         | "/path?query"                                   | ""                 | null
    false          | "https://host:0"                                      | "https://host/"                                 | ""                 | null
    false          | "https://host/path"                                   | "https://host/path"                             | ""                 | null
    false          | "http://host:99/path?query#fragment"                  | "http://host:99/path?query#fragment"            | ""                 | null
    true           | null                                                  | null                                            | null               | null
    true           | ""                                                    | "/"                                             | null               | null
    true           | "/path?encoded+%28query%29%3F"                        | "/path?encoded+(query)?"                        | "encoded+(query)?" | null
    true           | "https://host:0"                                      | "https://host/"                                 | null               | null
    true           | "https://host/path"                                   | "https://host/path"                             | null               | null
    true           | "http://host:99/path?query#encoded+%28fragment%29%3F" | "http://host:99/path?query#encoded+(fragment)?" | "query"            | "encoded+(fragment)?"

    req = [url: url == null ? null : new URI(url)]
  }

  def "test onResponse"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_ERROR_STATUSES, "$errorRange") {
      decorator.onResponse(span, resp)
    }

    then:
    if (status) {
      1 * span.setAttribute(Tags.HTTP_STATUS, status)
    }
    if (error) {
      1 * span.setStatus(Status.UNKNOWN)
    }
    0 * _

    where:
    status | error | errorRange | resp
    200    | false | null       | [status: 200]
    399    | false | null       | [status: 399]
    400    | true  | null       | [status: 400]
    499    | true  | null       | [status: 499]
    500    | true  | null       | [status: 500]
    500    | false | "400-499"  | [status: 500]
    500    | true  | "400-500"  | [status: 500]
    600    | false | null       | [status: 600]
    null   | false | null       | [status: null]
    null   | false | null       | null
  }

  def "test assert null span"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onRequest((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onResponse((Span) null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newDecorator(String serviceName = "test-service") {
    return new HttpClientDecorator<Map, Map>() {

      @Override
      protected String service() {
        return serviceName
      }

      @Override
      protected String getComponentName() {
        return "test-component"
      }

      @Override
      protected String method(Map m) {
        return m.method
      }

      @Override
      protected URI url(Map m) {
        return m.url
      }

      @Override
      protected String hostname(Map m) {
        return m.host
      }

      @Override
      protected Integer port(Map m) {
        return m.port
      }

      @Override
      protected Integer status(Map m) {
        return m.status
      }
    }
  }
}
