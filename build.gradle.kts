import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.io.ByteArrayOutputStream

plugins {
  id("java")
  id("idea")
  id("checkstyle")
  id("jacoco")

  id("com.github.spotbugs") version "6.0.19"
  id("com.diffplug.spotless") version "6.25.0"
  id("com.github.ben-manes.versions") version "0.51.0"
  id("io.freefair.lombok") version "8.6"
  id("org.sonarqube") version "5.1.0.4882"

  id("org.springframework.boot") version "3.3.1" apply false
  id("org.graalvm.buildtools.native") version "0.10.2" apply false
  id("io.spring.dependency-management") version "1.1.6"
}

group = "com.ukonnra"
version = "0.0.1-SNAPSHOT"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_21.majorVersion)
  }
}

allprojects {
  repositories {
    mavenCentral()
  }
}

tasks.wrapper {
  distributionType = Wrapper.DistributionType.ALL
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "idea")
  apply(plugin = "checkstyle")
  apply(plugin = "jacoco")
  apply(plugin = "jacoco-report-aggregation")

  apply(plugin = "com.github.spotbugs")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "io.freefair.lombok")
  apply(plugin = "org.sonarqube")

  apply(plugin = "io.spring.dependency-management")

  dependencyManagement {
    imports {
      mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
  }

  checkstyle {
    toolVersion = "10.17.0"
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

  if (plugins.hasPlugin(JavaPlugin::class)) {
    apply<LibraryPlugin>()
  }

  if (plugins.hasPlugin(SpringBootPlugin::class)) {
    apply<ApplicationPlugin>()
  }


}

tasks.register<JacocoReport>("codeCoverageReport") {
  subprojects {
    plugins.withType<JacocoPlugin>().configureEach {
      this@subprojects.tasks.matching { it.extensions.findByType<JacocoTaskExtension>() != null }.configureEach {
        if (extensions.getByType<JacocoTaskExtension>().isEnabled) {
          sourceSets(this@subprojects.sourceSets.main.get())
          executionData(this)
        } else {
          logger.warn("Jacoco extension is disabled for test task \'${name}\' in project \'${this@subprojects.name}\'. this test task will be excluded from jacoco report.")
        }
      }

      this@subprojects.tasks.matching { it.extensions.findByType<JacocoTaskExtension>() != null }.forEach {
        this@register.dependsOn(it)
      }
    }
  }

  reports {
    xml.required.set(true)
    html.required.set(true)
  }
}


// Plugins

class LibraryPlugin : Plugin<Project> {
  override fun apply(project: Project) {

  }
}

class ApplicationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    extensions.configure<SpringBootExtension> {
      buildInfo()
    }

    extensions.configure<GraalVMExtension> {
      binaries.all {
        // Windows: Fix unknown error for: `Error: Classes that should be initialized at run time got initialized during image building`
        buildArgs.add("--initialize-at-build-time=org.apache.catalina.connector.RequestFacade,org.apache.catalina.connector.ResponseFacade")
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
            commandLine("jar", "xf", jarLocation)
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

