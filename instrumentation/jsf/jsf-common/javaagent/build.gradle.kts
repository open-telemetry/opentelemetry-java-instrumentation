plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("jakarta.faces:jakarta.faces-api:2.3.2")
  compileOnly("jakarta.el:jakarta.el-api:3.0.3")
}
