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
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  library("io.projectreactor.netty:reactor-netty-http:1.0.0")

  testInstrumentation(project(":instrumentation:reactor-netty:reactor-netty-0.9:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor-3.1:javaagent"))
}

tasks {
  val testConnectionSpan by registering(Test::class) {
    filter {
      includeTestsMatching("ReactorNettyConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
    include("**/ReactorNettyConnectionSpanTest.*")
    jvmArgs("-Dotel.instrumentation.reactor-netty.always-create-connect-span=true")
  }

  named<Test>("test") {
    dependsOn(testConnectionSpan)
    filter {
      excludeTestsMatching("ReactorNettyConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}
