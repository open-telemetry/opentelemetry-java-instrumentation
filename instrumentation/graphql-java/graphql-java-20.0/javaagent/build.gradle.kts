plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.graphql-java")
    module.set("graphql-java")
    versions.set("[20,)")
    skip("230521-nf-execution")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:graphql-java:graphql-java-20.0:library"))
  implementation(project(":instrumentation:graphql-java:graphql-java-common:library"))

  library("com.graphql-java:graphql-java:20.0")

  testInstrumentation(project(":instrumentation:graphql-java:graphql-java-12.0:javaagent"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common:testing"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.graphql.data-fetcher.enabled=true")
}

if (findProperty("testLatestDeps") as Boolean) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
