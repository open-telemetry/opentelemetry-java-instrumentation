plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.amazonaws")
    module.set("aws-lambda-java-core")
    versions.set("[1.0.0,)")
    extraDependency("com.amazonaws.serverless:aws-serverless-java-container-core:1.5.2")
  }
}

dependencies {
  compileOnly(project(":javaagent-bootstrap"))

  implementation(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:library"))

  library("com.amazonaws:aws-lambda-java-core:1.0.0")

  testImplementation(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:testing"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
