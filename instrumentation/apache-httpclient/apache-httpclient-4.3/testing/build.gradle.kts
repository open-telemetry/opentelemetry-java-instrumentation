plugins {
  id("otel.java-conventions")
}

tasks {
  compileJava {
    // when code is compiled with jdk 21 and executed with jdk 8 -parameters flag is needed to avoid
    // java.lang.reflect.MalformedParametersException: Invalid parameter name ""
    // when junit calls java.lang.reflect.Executable.getParameters() on the constructor of a
    // non-static nested test class
    options.compilerArgs.add("-parameters")
  }
}

dependencies {
  api(project(":testing-common"))

  api("org.apache.httpcomponents:httpclient:4.3")

  implementation("io.opentelemetry:opentelemetry-api")
}
