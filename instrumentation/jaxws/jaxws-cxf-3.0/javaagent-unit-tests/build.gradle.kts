plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")
  compileOnly("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")

  testImplementation(project(":instrumentation:jaxws:jaxws-cxf-3.0:javaagent"))

  testImplementation("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")
}
