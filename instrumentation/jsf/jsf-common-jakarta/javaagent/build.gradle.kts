plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("jakarta.faces:jakarta.faces-api:3.0.0")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")
}
