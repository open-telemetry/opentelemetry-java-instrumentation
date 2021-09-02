plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

dependencies {
  implementation("com.couchbase.client:tracing-opentelemetry:0.3.6")
}

tasks {
  shadowJar {
    dependencies {
      // including only tracing-opentelemetry excludes its transitive dependencies
      include(dependency("com.couchbase.client:tracing-opentelemetry"))
    }
    relocate("com.couchbase.client.tracing.opentelemetry", "com.couchbase.v3_1_6.client.tracing.opentelemetry")
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
  }
}
