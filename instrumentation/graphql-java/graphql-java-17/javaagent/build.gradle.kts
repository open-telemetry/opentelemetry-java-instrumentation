plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.graphql-java")
    module.set("graphql-java")
    versions.set("[17,)") // TODO
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:graphql-java:graphql-java-17:library"))

  library("com.graphql-java:graphql-java:17.0")

  testImplementation(project(":instrumentation:graphql-java:graphql-java-17:testing"))
}
