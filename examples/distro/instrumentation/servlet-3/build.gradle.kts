plugins {
  id("otel.instrumentation-conventions")
  id("io.opentelemetry.instrumentation.muzzle-generation")
  id("io.opentelemetry.instrumentation.muzzle-check")
}

muzzle {
  pass {
    group.set("javax.servlet")
    module.set("javax.servlet-api")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("javax.servlet")
    module.set("servlet-api")
    versions.set("[2.2, 3.0)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly(project(":bootstrap"))
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling:$opentelemetryJavaagentAlphaVersion")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support:$opentelemetryJavaagentAlphaVersion")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:$opentelemetryJavaagentAlphaVersion")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling:$opentelemetryJavaagentAlphaVersion")

  testInstrumentation("io.opentelemetry.javaagent.instrumentation:opentelemetry-javaagent-servlet-common:$opentelemetryJavaagentAlphaVersion")
  testInstrumentation("io.opentelemetry.javaagent.instrumentation:opentelemetry-javaagent-servlet-2.2:$opentelemetryJavaagentAlphaVersion")
  testInstrumentation("io.opentelemetry.javaagent.instrumentation:opentelemetry-javaagent-servlet-3.0:$opentelemetryJavaagentAlphaVersion")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common") {
    exclude(group = "org.eclipse.jetty", module = "jetty-server")
  }

  testImplementation("com.squareup.okhttp3:okhttp:5.3.2")
  testImplementation("javax.servlet:javax.servlet-api:3.0.1")
  testImplementation("org.eclipse.jetty:jetty-server:8.2.0.v20160908")
  testImplementation("org.eclipse.jetty:jetty-servlet:8.2.0.v20160908")
}
