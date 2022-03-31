plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.graphql-java")
    module.set("graphql-java")
    versions.set("[12,)")
    skip("230521-nf-execution")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:graphql-java-12.0:library"))

  library("com.graphql-java:graphql-java:12.0")

  testImplementation(project(":instrumentation:graphql-java-12.0:testing"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.graphql.experimental-span-attributes=true")
}
