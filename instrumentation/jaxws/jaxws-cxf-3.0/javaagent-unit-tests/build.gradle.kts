plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:jaxws:jaxws-cxf-3.0:javaagent"))

  testImplementation("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")
}
