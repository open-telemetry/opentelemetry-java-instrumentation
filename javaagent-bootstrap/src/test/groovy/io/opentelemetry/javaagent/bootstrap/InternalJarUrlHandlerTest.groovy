/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap

import spock.lang.Shared
import spock.lang.Specification

class InternalJarUrlHandlerTest extends Specification {

  @Shared
  URL testJarLocation = new File("src/test/resources/classloader-test-jar/testjar-jdk8").toURI().toURL()

  def "test get URL"() {
    setup:
    InternalJarUrlHandler handler = new InternalJarUrlHandler(dir, testJarLocation)
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

  // this test serves two purposes:
  //
  // (1) Constrain what is cached: I wanted to test that a load does not cache the stream after
  // releasing it and losing control over its lifecyle.
  // What's special about the second load which makes it distinct from the first load is that there
  // was a predecessor.
  // There is a caching mechanism in place, (the JarEntry is cached with the class name), but what
  // if someone comes along later and tried to cache something closeable like the stream instead?
  // That would be unfortunate, but this test would break if that ever happened.
  //
  // (2) Hitting the branch where caching has occurred: there happen to be about 800 classloads
  // early on which go all the way down the chain and end up in the leaf-level classloader twice.
  // Preventing this from happening would have been a much more invasive change than mitigating it
  // with a cache.
  // Unless you test the load twice, you won't test the branch where the cache is hit.
  // The logic actually dismisses the cache after a hit, based on knowledge of this pattern, making
  // the third load indistinguishable from the first load, hence the lack of a test for it.
  def "test read class twice"() {
    setup:
    InternalJarUrlHandler handler = new InternalJarUrlHandler(dir, testJarLocation)
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
    InternalJarUrlHandler handler = new InternalJarUrlHandler(dir, testJarLocation)
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
