plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:20.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks.test {
  jvmArgs("-Dotel.instrumentation.graphql.data-fetcher.enabled=true")
}

tasks {
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(
      "-Dotel.instrumentation.common.v3-preview=true",
      "-Dotel.instrumentation.graphql.data-fetcher.enabled=true",
    )
  }

  check {
    dependsOn(testV3Preview)
  }
}
