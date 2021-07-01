plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

dependencies {
  implementation(project(":javaagent-bootstrap"))
  implementation(project(":benchmark-integration"))

  implementation("org.eclipse.jetty:jetty-server:9.4.1.v20170120")
  implementation("org.eclipse.jetty:jetty-servlet:9.4.1.v20170120")
}

tasks {
  jar {
    manifest {
      attributes("Main-Class" to "io.opentelemetry.perftest.jetty.JettyPerftest")
    }
  }
}
