/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import spock.lang.Unroll

abstract class WildflySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-wildfly"
  }

  @Unroll
  def "JSP smoke test on WildFly"() {
    when:
    def response = client().get("/app/jsp").aggregate().join()
    TraceInspector traces = new TraceInspector(waitForTraces())
    String responseBody = response.contentUtf8()

    then:
    response.status().isSuccess()
    responseBody.contains("Successful JSP test")

    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    traces.countSpansByName('/app/jsp') == 1

    where:
    [appServer, jdk] << getTestParams()
  }
}

@AppServer(version = "13.0.0.Final", jdk = "8")
class Wildfly13Jdk8 extends WildflySmokeTest {
}
@AppServer(version = "13.0.0.Final", jdk = "8-openj9")
class Wildfly13Jdk8Openj9 extends WildflySmokeTest {
}
@AppServer(version = "17.0.1.Final", jdk = "8")
class Wildfly17Jdk8 extends WildflySmokeTest {
}
@AppServer(version = "17.0.1.Final", jdk = "11")
class Wildfly17Jdk11 extends WildflySmokeTest {
}
@AppServer(version = "17.0.1.Final", jdk = "17")
class Wildfly17Jdk17 extends WildflySmokeTest {
}
@AppServer(version = "21.0.0.Final", jdk = "8")
class Wildfly21Jdk8 extends WildflySmokeTest {
}
@AppServer(version = "21.0.0.Final", jdk = "11")
class Wildfly21Jdk11 extends WildflySmokeTest {
}
@AppServer(version = "21.0.0.Final", jdk = "17")
class Wildfly21Jdk17 extends WildflySmokeTest {
}
@AppServer(version = "25.0.1.Final", jdk = "8")
class Wildfly25Jdk8 extends WildflySmokeTest {
}
@AppServer(version = "25.0.1.Final", jdk = "11")
class Wildfly25Jdk11 extends WildflySmokeTest {
}
@AppServer(version = "25.0.1.Final", jdk = "17")
class Wildfly25Jdk17 extends WildflySmokeTest {
}
@AppServer(version = "17.0.1.Final", jdk = "8-openj9")
class Wildfly17Jdk8Openj9 extends WildflySmokeTest {
}
@AppServer(version = "17.0.1.Final", jdk = "11-openj9")
class Wildfly17Jdk11Openj9 extends WildflySmokeTest {
}
@AppServer(version = "17.0.1.Final", jdk = "16-openj9")
class Wildfly17Jdk16Openj9 extends WildflySmokeTest {
}
@AppServer(version = "21.0.0.Final", jdk = "8-openj9")
class Wildfly21Jdk8Openj9 extends WildflySmokeTest {
}
@AppServer(version = "21.0.0.Final", jdk = "11-openj9")
class Wildfly21Jdk11Openj9 extends WildflySmokeTest {
}
@AppServer(version = "21.0.0.Final", jdk = "16-openj9")
class Wildfly21Jdk16Openj9 extends WildflySmokeTest {
}
@AppServer(version = "25.0.1.Final", jdk = "8-openj9")
class Wildfly25Jdk8Openj9 extends WildflySmokeTest {
}
@AppServer(version = "25.0.1.Final", jdk = "11-openj9")
class Wildfly25Jdk11Openj9 extends WildflySmokeTest {
}
@AppServer(version = "25.0.1.Final", jdk = "16-openj9")
class Wildfly25Jdk16Openj9 extends WildflySmokeTest {
}