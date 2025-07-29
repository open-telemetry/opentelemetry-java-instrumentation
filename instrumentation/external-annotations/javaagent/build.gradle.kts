plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  compileOnly(project(":javaagent-tooling"))

  testImplementation("com.newrelic.agent.java:newrelic-api:5.14.0")
  testImplementation("io.opentracing.contrib.dropwizard:dropwizard-opentracing:0.2.2") {
    isTransitive = false
  }
  testImplementation("com.datadoghq:dd-trace-api:1.43.0")
  testImplementation("com.signalfx.public:signalfx-trace-api:0.48.0-sfx8")
  // Old and new versions of kamon use different packages for Trace annotation
  testImplementation("io.kamon:kamon-annotation_2.11:0.6.7") {
    isTransitive = false
  }
  testImplementation("io.kamon:kamon-annotation-api:2.1.4")
  testImplementation("com.appoptics.agent.java:appoptics-sdk:6.20.1")
  testImplementation("com.tracelytics.agent.java:tracelytics-api:5.0.10")
  testImplementation("org.springframework.cloud:spring-cloud-sleuth-core:2.2.4.RELEASE") {
    isTransitive = false
  }
  // For some annotations used by sleuth
  testCompileOnly("org.springframework:spring-core:4.3.30.RELEASE")
}

tasks {
  val testIncludeProperty by registering(Test::class) {
    filter {
      includeTestsMatching("ConfiguredTraceAnnotationsTest")
    }
    include("**/ConfiguredTraceAnnotationsTest.*")
    jvmArgs("-Dotel.instrumentation.external-annotations.include=io.opentelemetry.javaagent.instrumentation.extannotations.OuterClass\$InterestingMethod")
  }

  val testExcludeMethodsProperty by registering(Test::class) {
    filter {
      includeTestsMatching("TracedMethodsExclusionTest")
    }
    include("**/TracedMethodsExclusionTest.*")
    jvmArgs(
      "-Dotel.instrumentation.external-annotations.exclude-methods=io.opentelemetry.javaagent.instrumentation.extannotations.TracedMethodsExclusionTest\$TestClass[excluded,annotatedButExcluded]"
    )
  }

  test {
    filter {
      excludeTestsMatching("ConfiguredTraceAnnotationsTest")
      excludeTestsMatching("TracedMethodsExclusionTest")
    }
  }

  check {
    dependsOn(testIncludeProperty)
    dependsOn(testExcludeMethodsProperty)
  }
}
