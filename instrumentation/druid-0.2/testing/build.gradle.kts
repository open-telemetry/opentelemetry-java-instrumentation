plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("org.mockito:mockito-core")
  api("org.mockito:mockito-junit-jupiter")

  compileOnly("com.alibaba:druid:0.2.6")
}
