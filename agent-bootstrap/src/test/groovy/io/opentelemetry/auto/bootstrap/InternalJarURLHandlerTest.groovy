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

package datadog.trace.bootstrap

import io.opentelemetry.auto.bootstrap.InternalJarURLHandler
import io.opentelemetry.auto.util.test.AgentSpecification
import spock.lang.Shared

class InternalJarURLHandlerTest extends AgentSpecification {

  @Shared
  URL testJarLocation = new File("src/test/resources/classloader-test-jar/testjar-jdk8").toURI().toURL()

  def "test get URL"() {
    setup:
    InternalJarURLHandler handler = new InternalJarURLHandler(dir, testJarLocation)
    when:
    URLConnection connection = handler.openConnection(new URL('file://' + file))
    assert connection != null
    byte[] data = new byte[128]
    int read = connection.getInputStream().read(data)
    then:
    read > 0

    where:
    dir        | file
    "isolated" | '/a/A.class'
    "isolated" | '/a/b/B.class'
    "isolated" | '/a/b/c/C.class'
  }


  // guards against InternalJarURLHandler caching and re-using the same stream twice
  def "test read class twice"() {
    setup:
    InternalJarURLHandler handler = new InternalJarURLHandler(dir, testJarLocation)
    when:
    URLConnection connection = handler.openConnection(new URL('file://' + file))
    assert connection != null
    InputStream is = connection.getInputStream()
    is.close()
    connection = handler.openConnection(new URL('file://' + file))
    assert connection != null
    is = connection.getInputStream()
    byte[] data = new byte[128]
    int read = is.read(data)

    then:
    read > 0

    where:
    dir        | file
    "isolated" | '/a/A.class'
    "isolated" | '/a/b/B.class'
    "isolated" | '/a/b/c/C.class'
  }

  def "handle not found"() {
    setup:
    InternalJarURLHandler handler = new InternalJarURLHandler(dir, testJarLocation)
    when:
    handler.openConnection(new File(file).toURI().toURL())
    then:
    // not going to specify (and thereby constrain) the sub type because it doesn't matter
    thrown IOException

    // permuted
    where:
    dir        | file
    "isolated" | '/x/X.class'
  }
}
