import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins {
  id("otel.spotless-conventions")

  id("com.bmuschko.docker-remote-api")
}

data class ImageTarget(
  val version: List<String>,
  val vm: List<String>,
  val jdk: List<String>,
  val args: Map<String, String> = emptyMap(),
  val war: String = "servlet-3.0",
  val windows: Boolean = true
)

val extraTag = findProperty("extraTag")
  ?: java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(java.time.LocalDateTime.now())

val latestJava = "25" // renovate(java-version)

// Each line under appserver describes one matrix of (version x vm x jdk), dockerfile key overrides
// Dockerfile name, args key passes raw arguments to docker build
val targets = mapOf(
  "jetty" to listOf(
    ImageTarget(
      listOf("9.4.58"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava),
      mapOf("sourceVersion" to "9.4.58.v20250814")
    ),
    ImageTarget(
      listOf("10.0.26"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      mapOf("sourceVersion" to "10.0.26")
    ),
    ImageTarget(
      listOf("11.0.26"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      mapOf("sourceVersion" to "11.0.26"),
      "servlet-5.0"
    ),
    ImageTarget(
      listOf("12.0.28"),
      listOf("hotspot", "openj9"),
      listOf("17", "21", latestJava),
      mapOf("sourceVersion" to "12.0.28"),
      "servlet-5.0"
    ),
  ),
  "liberty" to listOf(
    ImageTarget(
      listOf("open-liberty:20.0.0.12-full-java11-openj9@sha256:2fa4af95d6c48e3db79edfd2b8a9c71e26c63a68c3fcae92f222fbb42c469ed2"),
      listOf("hotspot", "openj9"),
      listOf("8", "11"),
      mapOf("release" to "2020-11-11_0736")
    ),
    ImageTarget(
      listOf("open-liberty:21.0.0.12-full-java11-openj9@sha256:eb014c600b5e08b799cb0c5781e606cf1e7a28ad913ba956c9d9e7f8a2f528dc"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17"),
      mapOf("release" to "2021-11-17_1256")
    ),
    ImageTarget(
      listOf("open-liberty:22.0.0.12-full-java11-openj9@sha256:a06f1da35a564f00354b86c7d01d8cc9d6eef156ce88d5b59605c5c02bf48c72"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17"),
      mapOf("release" to "22.0.0.12")
    ),
    ImageTarget(
      listOf("open-liberty:23.0.0.12-full-java11-openj9@sha256:cd6aa69cffffb45427cbb6a5640cd00b13c98064f296a66894ea1decd181e1c3"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21"),
      mapOf("release" to "23.0.0.12")
    ),
  ),
  "payara" to listOf(
    ImageTarget(
      listOf(
        "payara/server-full:5.2020.6@sha256:8c8f054ecbfb340b60961d7ffea2d223cea1afe6183f6986f4806de5c0bc9419",
        "payara/server-full:5.2021.8@sha256:ffc915a7243b27504c13c4bd4adb3da55c6c08a93ac05685afea3ea77380109d"
      ),
      listOf("hotspot", "openj9"),
      listOf("8", "11")
    ),
    // Test application is not deployed when server is sarted with hotspot jdk version 21
    ImageTarget(
      listOf("payara/server-full:6.2023.12@sha256:5c382db1f5bad8bef693dbcd0fed299844690839f4894e07c248de5f4b186b9b"),
      listOf("hotspot"),
      listOf("11", "17"),
      war = "servlet-5.0"
    ),
    ImageTarget(
      listOf("payara/server-full:6.2023.12@sha256:5c382db1f5bad8bef693dbcd0fed299844690839f4894e07c248de5f4b186b9b"),
      listOf("openj9"),
      listOf("11", "17", "21"),
      war = "servlet-5.0"
    )
  ),
  "tomcat" to listOf(
    ImageTarget(
      listOf("7.0.109"),
      listOf("hotspot", "openj9"),
      listOf("8"),
      mapOf("majorVersion" to "7")
    ),
    ImageTarget(
      listOf("8.5.98"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava),
      mapOf("majorVersion" to "8")
    ),
    ImageTarget(
      listOf("9.0.111"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava),
      mapOf("majorVersion" to "9")
    ),
    ImageTarget(
      listOf("10.1.48"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      mapOf("majorVersion" to "10"),
      "servlet-5.0"
    ),
  ),
  "tomee" to listOf(
    ImageTarget(
      listOf("7.0.9", "7.1.4"),
      listOf("hotspot", "openj9"),
      listOf("8")
    ),
    ImageTarget(
      listOf("8.0.16"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava)
    ),
    ImageTarget(
      listOf("9.1.3"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      war = "servlet-5.0"
    ),
  ),
  "websphere" to listOf(
    ImageTarget(
      listOf(
        "ibmcom/websphere-traditional:8.5.5.22@sha256:2a385c56f3e6781cc595d873473efd5ef7cb4f34e88c6cf8381121332fb49c9c",
        "ibmcom/websphere-traditional:9.0.5.14@sha256:7e569af2f4050bb0f3ac0fcab113e2dee20d9d6bdc4061cef4b97b79c2ea4fdd"
      ),
      listOf("openj9"),
      listOf("8"),
      windows = false
    ),
  ),
  "wildfly" to listOf(
    ImageTarget(
      listOf("13.0.0.Final"),
      listOf("hotspot", "openj9"),
      listOf("8")
    ),
    ImageTarget(
      listOf("17.0.1.Final", "21.0.0.Final"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21")
    ),
    ImageTarget(
      listOf("28.0.1.Final", "29.0.1.Final", "30.0.1.Final"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21"),
      war = "servlet-5.0"
    ),
  ),
)

tasks {
  val buildLinuxTestImages by registering {
    group = "build"
    description = "Builds all Linux Docker images for the test matrix"
  }

  val buildWindowsTestImages by registering {
    group = "build"
    description = "Builds all Windows Docker images for the test matrix"
  }

  val linuxImages = createDockerTasks(buildLinuxTestImages, false)
  val windowsImages = createDockerTasks(buildWindowsTestImages, true)

  val pushLinuxImages by registering(DockerPushImage::class) {
    dependsOn(buildLinuxTestImages)
    group = "publishing"
    description = "Push Linux Docker images for the test matrix"
    images.set(linuxImages)
  }

  val pushWindowsImages by registering(DockerPushImage::class) {
    dependsOn(buildWindowsTestImages)
    group = "publishing"
    description = "Push Windows Docker images for the test matrix"
    images.set(windowsImages)
  }

  val printSmokeTestsConfigurations by registering {
    doFirst {
      for ((server, matrices) in targets) {
        val smokeTestServer = findProperty("smokeTestServer")
        if (smokeTestServer != null && server != smokeTestServer) {
          continue
        }
        println(server)
        val serverName = server.replaceFirstChar(Char::uppercase)
        for (entry in matrices) {
          for (version in entry.version) {
            val dotIndex = version.indexOf('.')
            val majorVersion = if (dotIndex != -1) version.substring(0, dotIndex) else version
            for (jdk in entry.jdk) {
              for (vm in entry.vm) {
                println("@AppServer(version = \"$version\", jdk = \"$jdk${if (vm == "hotspot") "" else "-openj9"}\")")
                println("class ${serverName}${majorVersion}Jdk${jdk}${if (vm == "hotspot") "" else "Openj9"} extends ${serverName}SmokeTest {")
                println("}")
              }
            }
          }
        }
      }
    }
  }
}

fun configureImage(
  parentTask: TaskProvider<out Task>,
  server: String,
  dockerfile: String,
  fullVersion: String,
  vm: String,
  jdk: String,
  warProject: String,
  args: Map<String, String>,
  isWindows: Boolean
): String {
  val versionParts = fullVersion.split("@")
  val imageNameWithTag = versionParts[0]
  val serverImageHash = if (versionParts.size > 1) versionParts[1].removePrefix("sha256:") else ""

  // Extract just the version (tag) from the full image reference
  var version = if (imageNameWithTag.contains(":")) {
    imageNameWithTag.substringAfterLast(":")
  } else {
    imageNameWithTag
  }

  // Extract just the version number from tags with suffixes
  // (e.g., "20.0.0.12" from "20.0.0.12-full-java11-openj9")
  if (version.contains("-")) {
    version = version.substringBefore("-")
  }

  val dockerFileName = "$dockerfile.${if (isWindows) "windows." else ""}dockerfile"
  val platformSuffix = if (isWindows) "-windows" else ""

  // Using separate build directory for different images
  val dockerWorkingDir = layout.buildDirectory.dir("docker-$server-$version-jdk$jdk-$vm-$warProject-$platformSuffix")

  val prepareTask = tasks.register<Copy>("${server}ImagePrepare-$version-jdk$jdk-$vm$platformSuffix") {
    val warTask = project(":smoke-tests:images:servlet:$warProject").tasks.named<War>("war")
    dependsOn(warTask)
    into(dockerWorkingDir)
    from("src/$dockerFileName")
    from("src/main/docker/$server")
    from(warTask.get().archiveFile) {
      rename { "app.war" }
    }
  }

  val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"
  val vmSuffix = if (vm == "hotspot") "" else "-$vm"
  val image = "ghcr.io/$repo/smoke-test-servlet-$server:$version-jdk$jdk$vmSuffix$platformSuffix-$extraTag"

  val jdkImage = if (vm == "hotspot") {
    if (jdk == "26-ea") {
      // "The only tags which will continue to receive updates beyond July 2022 will be Early Access
      // builds (which are sourced from jdk.java.net), as those are not published/supported by any
      // of the above projects."
      // (see https://hub.docker.com/_/openjdk)
      "openjdk:$jdk"
    } else if (isWindows) {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk-windowsservercore-ltsc2022@sha256:2f2dc58147a9877ecde8644961b1e3c0f26f838af038ec8b8fc04dfbea61a4d0"
        "11" -> "eclipse-temurin:11.0.30_7-jdk-windowsservercore-ltsc2022@sha256:3a22b2c09f5394233833de5b00d075ef695b9a007a8dfcbcc287953e38557c0e"
        "17" -> "eclipse-temurin:17.0.18_8-jdk-windowsservercore-ltsc2022@sha256:0235ab5e21fb71769eaa01bec3eaf8e8a8b3aaf3a3840ab156c72f413335ddce"
        "21" -> "eclipse-temurin:21.0.10_7-jdk-windowsservercore-ltsc2022@sha256:9c6fac7352da02f3b1620dc7cc0b69b49dc93f72d2856a4d98856864c3329833"
        "25" -> "eclipse-temurin:25.0.2_10-jdk-windowsservercore-ltsc2022@sha256:3d7aefc693f5b2c88ad08457fb7f96f417fa3e324e112153dbd4050d5853f106"
        else -> throw GradleException("Unexpected jdk version for Windows: $jdk")
      }
    } else {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk@sha256:0b793df1b9217f3d25c5f820d47e85a20b0a78b0ccd0ab6deb9051502493c855"
        "11" -> "eclipse-temurin:11.0.30_7-jdk@sha256:8502bf560d73bbd1498cf7d70a9f6ce5c8fffa1322e809106fcbf4e3356c1666"
        "17" -> "eclipse-temurin:17.0.18_8-jdk@sha256:330029ffc8a75f4c74ed35c882378e1854a6f7ebdc53c43f25b587dba0fd2f93"
        "21" -> "eclipse-temurin:21.0.10_7-jdk@sha256:c57968360245ecac4228c5a9cfb56d4a02f0a67143c7e4dbf9f4ef5beb5253ec"
        "25" -> "eclipse-temurin:25.0.2_10-jdk@sha256:5b691907413c1c67b1f2402a39c64736d02404e703aa6f688164be23f83f06c4"
        else -> throw GradleException("Unexpected jdk version for Linux: $jdk")
      }
    }
  } else if (vm == "openj9") {
    if (isWindows) {
      // ibm-semeru-runtimes doesn't publish windows images
      throw GradleException("Unexpected vm: $vm")
    } else {
      when (jdk) {
        "8" -> "ibm-semeru-runtimes:open-8u472-b08-jdk@sha256:779c0c1133ebac0d599012c5a908e67adaa993352072eac21d7ced8d6a47f14d"
        "11" -> "ibm-semeru-runtimes:open-11.0.29_7-jdk@sha256:00bbefbb2cf3690546338c0e4ba4cf85ec658f40de5b292e77774b55e8267d66"
        "17" -> "ibm-semeru-runtimes:open-17-jdk@sha256:783de4ccd338b7a4518a3ad8a7c9dc9dd5f0cda2cf14249040781689e170c456"
        "21" -> "ibm-semeru-runtimes:open-21.0.9_10-jdk@sha256:2edabc89c49cfa2b9f0c051aced57ca6dee81c2e6b8820a1257182e779b58a48"
        "25" -> "ibm-semeru-runtimes:open-25-jdk@sha256:47ee963799953f8c233a321e03e3007094d626d044864e065d44753e18ee7fd0"
        else -> throw GradleException("Unexpected jdk version for openj9: $jdk")
      }
    }
  } else {
    throw GradleException("Unexpected vm: $vm")
  }

  val jdkImageParts = jdkImage.split("@")
  val jdkImageName = jdkImageParts[0]
  val jdkImageHash = if (jdkImageParts.size > 1) jdkImageParts[1].removePrefix("sha256:") else ""

  val extraArgs = args.toMutableMap()

  // Pass the full image name with tag (for servers that need it)
  if (imageNameWithTag.contains(":")) {
    extraArgs["imageName"] = imageNameWithTag
  }

  if (server == "wildfly") {
    // wildfly url without .zip or .tar.gz suffix
    val majorVersion = version.substring(0, version.indexOf(".")).toInt()
    val serverBaseUrl = if (majorVersion >= 25) {
      "https://github.com/wildfly/wildfly/releases/download/$version/wildfly-$version"
    } else {
      "https://download.jboss.org/wildfly/$version/wildfly-$version"
    }
    extraArgs["baseDownloadUrl"] = serverBaseUrl
  } else if (server == "payara") {
    if (version == "5.2020.6") {
      extraArgs["domainName"] = "production"
    } else {
      extraArgs["domainName"] = "domain1"
    }
  }

  val buildTask = tasks.register<DockerBuildImage>("${server}Image-$version-jdk$jdk$vmSuffix$platformSuffix") {
    dependsOn(prepareTask)
    group = "build"
    description = "Builds Docker image with $server $version on JDK $jdk-$vm${if (isWindows) " on Windows" else ""}"

    inputDir.set(dockerWorkingDir)
    images.add(image)
    dockerFile.set(File(dockerWorkingDir.get().asFile, dockerFileName))
    buildArgs.set(extraArgs + mapOf("jdk" to jdk, "vm" to vm, "version" to version, "jdkImageName" to jdkImageName, "jdkImageHash" to jdkImageHash, "imageHash" to serverImageHash))
  }

  parentTask.configure {
    dependsOn(buildTask)
  }
  return image
}

fun createDockerTasks(parentTask: TaskProvider<out Task>, isWindows: Boolean): Set<String> {
  val resultImages = mutableSetOf<String>()
  for ((server, matrices) in targets) {
    val smokeTestServer = findProperty("smokeTestServer")
    if (smokeTestServer != null && server != smokeTestServer) {
      continue
    }

    for (entry in matrices) {
      val dockerfile = server
      val extraArgs = entry.args
      val warProject = entry.war
      val supportsWindows = entry.windows

      for (version in entry.version) {
        for (vm in entry.vm) {
          if (vm == "openj9" && isWindows) {
            // ibm-semeru-runtimes doesn't publish windows images
            continue
          }
          for (jdk in entry.jdk) {
            if (supportsWindows || !isWindows) {
              resultImages.add(configureImage(parentTask, server, dockerfile, version, vm, jdk, warProject, extraArgs, isWindows))
            }
          }
        }
      }
    }
  }
  return resultImages
}
