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
  api("javax.xml.ws:jaxws-api:2.0")
  api("javax.jws:javax.jws-api:1.1")

  api("org.eclipse.jetty:jetty-webapp:9.4.35.v20201120")
  api("org.springframework.ws:spring-ws-core:3.0.0.RELEASE")

  implementation(project(":testing-common"))

  // XJC tool dependencies
  xjcTool("com.sun.xml.bind:jaxb-xjc:2.3.3")
  xjcTool("com.sun.xml.bind:jaxb-impl:2.3.3")
  xjcTool("com.sun.xml.bind:jaxb-core:2.3.0.1")
}
