plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")
  compileOnly("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")

  implementation(project(":instrumentation:jaxws:jaxws-2.0-cxf-3.0:javaagent"))

  testImplementation(project(":instrumentation-api"))
  testImplementation("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")
}
