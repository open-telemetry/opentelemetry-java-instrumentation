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
  implementation(project(":instrumentation:netty:netty-4.1-common:javaagent"))
  library("io.projectreactor.netty:reactor-netty:0.9.0.RELEASE")

  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  latestDepTestLibrary("io.projectreactor.netty:reactor-netty:0.+") // see reactor-netty-1.0 modules
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

  test {
    dependsOn(testConnectionSpan)
    filter {
      excludeTestsMatching("ReactorNettyConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
  }
}
