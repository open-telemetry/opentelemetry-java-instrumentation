plugins {
  id("otel.java-conventions")
}

// Configuration for XJC code generation
val xjcTool by configurations.creating

val generateXjcSources = tasks.register<JavaExec>("generateXjcSources") {
  val schemaDir = file("src/main/schema")
  val outputDir = layout.buildDirectory.dir("generated/sources/xjc/java/main").get().asFile

  inputs.dir(schemaDir)
  outputs.dir(outputDir)

  classpath = xjcTool
  mainClass.set("com.sun.tools.xjc.XJCFacade")

  args(
    "-d",
    outputDir.absolutePath,
    "-p",
    "io.opentelemetry.test.hello_web_service",
    file("$schemaDir/hello.xsd").absolutePath
  )

  doFirst {
    outputDir.mkdirs()
  }
}

sourceSets {
  main {
    java {
      srcDir(generateXjcSources.map { it.outputs.files.singleFile })
    }
  }
}

tasks.compileJava {
  dependsOn(generateXjcSources)
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    // exclude generated web service classes
    exclude("**/hello_web_service/**")
  }
}

dependencies {
  api("jakarta.xml.ws:jakarta.xml.ws-api:3.0.0")
  api("jakarta.jws:jakarta.jws-api:3.0.0")

  api("org.eclipse.jetty:jetty-webapp:11.0.17")
  api("org.springframework.ws:spring-ws-core:4.0.0")

  implementation(project(":testing-common"))

  // XJC tool dependencies (using Jakarta/JAXB 3.0)
  xjcTool("com.sun.xml.bind:jaxb-xjc:3.0.2")
  xjcTool("com.sun.xml.bind:jaxb-impl:3.0.2")
}
