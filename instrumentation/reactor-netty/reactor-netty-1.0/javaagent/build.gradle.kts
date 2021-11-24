plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  fail {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty")
    versions.set("[,1.0.0)")
  }
  pass {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty-http")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:netty:netty-4.1-common:javaagent"))
  implementation(project(":instrumentation:reactor-3.1:library"))

  library("io.projectreactor.netty:reactor-netty-http:1.0.0")

  testInstrumentation(project(":instrumentation:reactor-netty:reactor-netty-0.9:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor-3.1:javaagent"))

  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testInstrumentation(project(":instrumentation:opentelemetry-annotations-1.0:javaagent"))
}

tasks {
  val testConnectionSpan by registering(Test::class) {
    filter {
      includeTestsMatching("ReactorNettyConnectionSpanTest")
      includeTestsMatching("ReactorNettyClientSslTest")
      isFailOnNoMatchingTests = false
    }
    include("**/ReactorNettyConnectionSpanTest.*", "**/ReactorNettyClientSslTest.*")
    jvmArgs("-Dotel.instrumentation.netty.ssl-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.reactor-netty.always-create-connect-span=true")
  }

  test {
    dependsOn(testConnectionSpan)
    filter {
      excludeTestsMatching("ReactorNettyConnectionSpanTest")
      excludeTestsMatching("ReactorNettyClientSslTest")
      isFailOnNoMatchingTests = false
    }
  }
}
