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
  testImplementation("com.signalfx.public:signalfx-trace-api:0.48.0-sfx1")
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
}

tasks {
  val testIncludeProperty by registering(Test::class) {
    filter {
      includeTestsMatching("ConfiguredTraceAnnotationsTest")
      isFailOnNoMatchingTests = false
    }
    include("**/ConfiguredTraceAnnotationsTest.*")
    jvmArgs("-Dotel.instrumentation.external-annotations.include=package.Class\$Name;OuterClass\$InterestingMethod")
  }

  val testExcludeMethodsProperty by registering(Test::class) {
    filter {
      includeTestsMatching("TracedMethodsExclusionTest")
      isFailOnNoMatchingTests = false
    }
    include("**/TracedMethodsExclusionTest.*")
    jvmArgs("-Dotel.instrumentation.external-annotations.exclude-methods=TracedMethodsExclusionTest\$TestClass[excluded,annotatedButExcluded]")
  }

  test {
    dependsOn(testIncludeProperty)
    dependsOn(testExcludeMethodsProperty)
    filter {
      excludeTestsMatching("ConfiguredTraceAnnotationsTest")
      excludeTestsMatching("TracedMethodsExclusionTest")
      isFailOnNoMatchingTests = false
    }
  }
}
