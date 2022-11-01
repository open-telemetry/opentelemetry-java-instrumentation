val stableVersion = "1.19.2"
val alphaVersion = "1.19.2-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
