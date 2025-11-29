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

val matrix = mutableListOf<String>()

tasks {
  val buildLinuxTestImages by registering {
    group = "build"
    description = "Builds all Linux Docker images for the test matrix"
  }

  val buildWindowsTestImages by registering {
    group = "build"
    description = "Builds all Windows Docker images for the test matrix"
  }

  val pushMatrix by registering(DockerPushImage::class) {
    mustRunAfter(buildLinuxTestImages)
    mustRunAfter(buildWindowsTestImages)
    group = "publishing"
    description = "Push all Docker images for the test matrix"
    images.set(matrix)
  }

  createDockerTasks(buildLinuxTestImages, false)
  createDockerTasks(buildWindowsTestImages, true)

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

  // Using separate build directory for different image
  val dockerWorkingDir = layout.buildDirectory.dir("docker-$server-$version-jdk$jdk-$vm-$warProject")
  val dockerFileName = "$dockerfile.${if (isWindows) "windows." else ""}dockerfile"
  val platformSuffix = if (isWindows) "-windows" else ""

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
        "8" -> "eclipse-temurin:8u472-b08-jdk-windowsservercore-ltsc2022@sha256:46d804b1c8a658fd84b8f3b3f39a1739b0f0ffccf41a682cea4847982de3bd08"
        "11" -> "eclipse-temurin:11.0.29_7-jdk-windowsservercore-ltsc2022@sha256:3b16568beff29ff623e7d72018cd6b08f4003964a342a907ad410a0b953f40e6"
        "17" -> "eclipse-temurin:17.0.17_10-jdk-windowsservercore-ltsc2022@sha256:7c9e423728d04540c0a30d68ca0922390665dfec20e012beb95861d80aa2dd70"
        "21" -> "eclipse-temurin:21.0.9_10-jdk-windowsservercore-ltsc2022@sha256:45a3d356d018942a497b877633f19db401828ecb2a1de3cda635b98d08bfbaeb"
        "25" -> "eclipse-temurin:25.0.1_8-jdk-windowsservercore-ltsc2022@sha256:556d727eb539fd9c6242e75d17e1a2bf59456ea8a37478cfbd6406ca6db0d2d1"
        else -> throw GradleException("Unexpected jdk version for Windows: $jdk")
      }
    } else {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk@sha256:b4e05de303ea02659ee17044d6b68caadfc462f1530f3a461482afee23379cdd"
        "11" -> "eclipse-temurin:11.0.29_7-jdk@sha256:189ce1c8831fa5bdd801127dad99f68a17615f81f4aa839b1a4aae693261929a"
        "17" -> "eclipse-temurin:17.0.17_10-jdk@sha256:5a66a3ffd8728ed6c76eb4ec674c37991ac679927381f71774f5aa44cf420082"
        "21" -> "eclipse-temurin:21.0.9_10-jdk@sha256:ec2005c536f3661c6ef1253292c9c623e582186749a3ef2ed90903d1aaf74640"
        "25" -> "eclipse-temurin:25.0.1_8-jdk@sha256:adc4533ea69967c783ac2327dac7ff548fcf6401a7e595e723b414c0a7920eb2"
        else -> throw GradleException("Unexpected jdk version for Linux: $jdk")
      }
    }
  } else if (vm == "openj9") {
    if (isWindows) {
      // ibm-semeru-runtimes doesn't publish windows images
      throw GradleException("Unexpected vm: $vm")
    } else {
      when (jdk) {
        "8" -> "ibm-semeru-runtimes:open-8u472-b08-jdk@sha256:63bb8aad02000edbc5f90222a018862f546a0ac21ec01d6b31af6202083297e8"
        "11" -> "ibm-semeru-runtimes:open-11.0.29_7-jdk@sha256:a0910e6646e71de764f56ea19238719cb150ffabb46c0f9d3323e4cb697d59dc"
        "17" -> "ibm-semeru-runtimes:open-17-jdk@sha256:ad9a76a79afef5f01b49d3a7487e017305cb76f7421cd88e9424ee1c96fe8c09"
        "21" -> "ibm-semeru-runtimes:open-21.0.9_10-jdk@sha256:bd69dbe68315b72ebfa0d708511176c3317dd0c500dc462e7041570983f14c49"
        "25" -> "ibm-semeru-runtimes:open-25-jdk@sha256:58f8efd0e2b137c19e192a3d1a36e9efe070d6f59784bc4a84f551e6c148b35c"
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
    doLast {
      matrix.add(image)
    }
  }

  parentTask.configure {
    dependsOn(buildTask)
  }
  return image
}

fun createDockerTasks(parentTask: TaskProvider<out Task>, isWindows: Boolean) {
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
}
