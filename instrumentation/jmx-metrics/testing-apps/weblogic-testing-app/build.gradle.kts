plugins {
  id("otel.java-conventions")
}

description = "Application used for synthetic WebLogic JMX metrics testing"

tasks {
  register<Jar>("weblogicTestAppJar") {
    archiveClassifier.set("")
    manifest {
      attributes(
        "Main-Class" to
          "io.opentelemetry.instrumentation.jmx.weblogictest.WeblogicTestApplication"
      )
    }
    from(sourceSets.main.get().output)
  }
}

configurations.create("weblogicTestApp") {
  isCanBeConsumed = true
  isCanBeResolved = false
}

artifacts {
  add("weblogicTestApp", tasks.named("weblogicTestAppJar"))
}
