val stableVersion = "1.33.6"
val alphaVersion = "1.33.6-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
