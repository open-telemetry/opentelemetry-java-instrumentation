plugins {
  id("otel.java-conventions")
}

group = "io.opentelemetry.instrumentation.jmx.cameltest"
description = "Application used for Camel JMX metrics testing"

val camelVersion = "4.17.0"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation("org.apache.camel:camel-main:$camelVersion")
  implementation("org.apache.camel:camel-management:$camelVersion")
  implementation("org.apache.camel:camel-direct:$camelVersion")
  implementation("org.apache.camel:camel-timer:$camelVersion")
  runtimeOnly("org.slf4j:slf4j-simple")
}

tasks {
  register<Jar>("camelTestAppJar") {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
      attributes("Main-Class" to "io.opentelemetry.instrumentation.jmx.cameltest.CamelTestApplication")
    }
    from(sourceSets.main.get().output)
    from({
      configurations.runtimeClasspath.get()
        .filter { it.name.endsWith(".jar") }
        .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
  }
}
