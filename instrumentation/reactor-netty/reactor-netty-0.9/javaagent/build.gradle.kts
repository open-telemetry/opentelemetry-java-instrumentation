plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty")
    versions.set("[0.9.0.RELEASE,1.0.0)")
  }
  fail {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty-http")
    versions.set("[1.0.0,)")
  }
}

dependencies {
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  library("io.projectreactor.netty:reactor-netty:0.9.0.RELEASE")

  testInstrumentation(project(":instrumentation:reactor-netty:reactor-netty-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor-3.1:javaagent"))

  latestDepTestLibrary("io.projectreactor.netty:reactor-netty:(,1.0.0)")
}

tasks {
  val testConnectionSpan by registering(Test::class) {
    filter {
      includeTestsMatching("ReactorNettyConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
    include("**/ReactorNettyConnectionSpanTest.*")
    jvmArgs("-Dotel.instrumentation.netty.always-create-connect-span=true")
  }

  named<Test>("test") {
    dependsOn(testConnectionSpan)
    filter {
      excludeTestsMatching("ReactorNettyConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
  }
}
