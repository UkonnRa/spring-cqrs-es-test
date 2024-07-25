import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.io.ByteArrayOutputStream

plugins {
  java
  idea
  checkstyle
  `jacoco-report-aggregation`

  id("com.github.spotbugs") version "6.0.24"
  id("com.diffplug.spotless") version "6.25.0"
  id("com.github.ben-manes.versions") version "0.51.0"
  id("io.freefair.lombok") version "8.10"
  id("org.sonarqube") version "5.1.0.4882"

  id("org.springframework.boot") version "3.3.4" apply false
  id("org.graalvm.buildtools.native") version "0.10.3" apply false
  id("io.spring.dependency-management") version "1.1.6"
}

group = "com.ukonnra"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of("21")
  }
}

tasks.wrapper {
  distributionType = Wrapper.DistributionType.ALL
}

allprojects {
  apply(plugin = "io.spring.dependency-management")

  version = "0.1.0"

  repositories {
    mavenCentral()
  }

  dependencyManagement {
    imports {
      mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "idea")
  apply(plugin = "checkstyle")
  apply(plugin = "jacoco")

  apply(plugin = "com.github.spotbugs")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "io.freefair.lombok")
  apply(plugin = "org.sonarqube")

  checkstyle {
    toolVersion = "10.18.1"
  }

  tasks.withType<Checkstyle> {
    exclude {
      val path = it.file.absolutePath
      path.contains("aot") || path.contains("generated")
    }
  }

  spotbugs {
    excludeFilter.set(file("$rootDir/config/spotbugs/exclude.xml"))
  }

  tasks.spotbugsMain {
    reports.create("xml") {
      required.set(true)
    }

    reports.create("html") {
      required.set(true)
    }
  }

  spotless {
    java {
      targetExclude("**/generated/**")
      importOrder()
      removeUnusedImports()
      googleJavaFormat()
    }
  }

  sonarqube {
    properties {
      property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "${projectDir.parentFile.path}/build/reports/jacoco/codeCoverageReport/codeCoverageReport.xml"
      )
    }
  }

  configurations {
    compileOnly {
      extendsFrom(configurations.annotationProcessor.get())
    }
  }

  tasks.clean {
    delete("out", "logs")
  }

  tasks.test {
    useJUnitPlatform()
    testLogging.apply {
      exceptionFormat = TestExceptionFormat.FULL
      showStackTraces = true
    }
  }

  afterEvaluate {
    if (plugins.hasPlugin("org.springframework.boot")) {
      apply(plugin = "org.graalvm.buildtools.native")

      extensions.configure<SpringBootExtension> {
        buildInfo()
      }

      extensions.configure<GraalVMExtension> {
        binaries.all {
          buildArgs.add("-H:+ReportExceptionStackTraces")
        }
      }

      tasks.withType<BootJar> {
        val jlinkTask = tasks.register("jlink") {
          group = "build"
          description = "Generate the JRE based on JLink"

          doLast {
            println("== jlink to create JRE for ${project.name}")
            val buildDir = layout.buildDirectory.get().asFile
            val jarLocation = "${project.name}-${version}.jar"
            exec {
              workingDir("${buildDir}/libs")
              commandLine("${System.getProperty("java.home")}/bin/jar", "xf", jarLocation)
            }

            val jdepsOutput = ByteArrayOutputStream()
            exec {
              workingDir("${buildDir}/libs")
              standardOutput = jdepsOutput

              val classpath = (file("${workingDir}/BOOT-INF/lib").listFiles() ?: arrayOf()).map {
                it.toRelativeString(workingDir)
              }.joinToString(File.pathSeparator)

              commandLine(
                "jdeps",
                "--ignore-missing-deps",
                "--recursive",
                "--print-module-deps",
                "--multi-release",
                java.sourceCompatibility.majorVersion,
                "--class-path",
                classpath,
                jarLocation
              )
            }

            file("${buildDir}/libs/app-jre").deleteRecursively()

            val jdeps =
              jdepsOutput.toString().split(",").filter { !it.startsWith("org.graalvm") }.joinToString(",")
            exec {
              workingDir("${buildDir}/libs")
              commandLine(
                "jlink",
                "--add-modules",
                jdeps,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--output",
                "app-jre",
              )
            }
          }
        }
        finalizedBy(jlinkTask)
      }
    }
  }
}

// Jacoco Aggregation Settings

dependencies {
  subprojects.map { subproject ->
    jacocoAggregation(subproject)
  }
}
