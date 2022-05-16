#!/bin/bash -e

grep -Po "val stableVersion = \"\K[0-9]+.[0-9]+.0" version.gradle.kts
