/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.util.version.GenericVersion
import spock.lang.Specification

class VersionTest extends Specification {

  def "test range request"() {
    setup:
    def predicate = new MuzzlePlugin.AcceptableVersions(new VersionRangeResult(new VersionRangeRequest()), [])
    expect:
    !predicate.test(new GenericVersion("10.1.0-rc2+19-8e20bb26"))
    !predicate.test(new GenericVersion("2.4.5.BUILD-SNAPSHOT"))
  }
}
