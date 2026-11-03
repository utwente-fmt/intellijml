import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // IntelliJ Platform Gradle Plugin
    id("org.jetbrains.intellij.platform") version "2.19.0"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.5.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Since 2025.3 IntelliJ IDEA ships as a single unified distribution (no separate Community Edition)
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        providers.gradleProperty("platformPlugins").get().split(',').map(String::trim).filter(String::isNotEmpty)
            .forEach { plugin(it) }

        pluginVerifier()

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }

        // Extract the <!-- Plugin description --> section from DESCRIPTION.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("DESCRIPTION.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in DESCRIPTION.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // Get the latest available change notes from the changelog file
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    getOrNull(properties("pluginVersion")) ?: getLatest(),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    pluginVerification {
        // New Marketplace uploads may not use "IntelliJ" in the plugin name. The plugin is distributed outside
        // the Marketplace under its existing name, so don't fail verification on that rule.
        freeArgs = listOf("-mute", "TemplateWordInPluginName")

        ides {
            // Entries are versions (2025.1.7) or build numbers (263.7307.44, e.g. for EAP builds).
            // Releases before 2025.3 (build 253) only exist as the Community Edition (IC).
            properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty).forEach {
                val parts = it.split('.').map(String::toInt)
                val branch = if (parts[0] >= 2000) (parts[0] % 100) * 10 + parts[1] else parts[0]
                val type = if (branch < 253) IntelliJPlatformType.IntellijIdeaCommunity else IntelliJPlatformType.IntellijIdea
                create(type, it)
            }
        }
    }

    buildSearchableOptions = false

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first())
    }
}

// Configure gradle-changelog-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version = properties("pluginVersion")
    groups = emptyList()
}

java {
    // The JDK must be able to read the target platform's class files (IntelliJ 2026.x is compiled for Java 25)
    toolchain {
        languageVersion = JavaLanguageVersion.of(properties("javaToolchainVersion"))
    }
}

sourceSets["main"].java.srcDirs("src/main/gen")
sourceSets["test"].java.srcDirs("src/test")

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    // Emit bytecode for the oldest supported platform (2025.x runs on Java 21)
    withType<JavaCompile> {
        options.release = properties("javaVersion").toInt()
    }

    test {
        // The Java test fixtures need the mock JDKs from https://github.com/JetBrains/intellij-community
        // (only the java/mockJDK-* directories are needed). Point to the checkout with
        // -PideaHomePath=... or the IDEA_HOME_PATH environment variable.
        // Passed as an argument provider because the IntelliJ Platform Gradle Plugin sets idea.home.path itself.
        val projectPath = layout.projectDirectory.asFile.toPath()
        val ideaHomePath = providers.gradleProperty("ideaHomePath").orElse(providers.environmentVariable("IDEA_HOME_PATH"))
            .map { projectPath.resolve(it).normalize().toString() }
        jvmArgumentProviders += CommandLineArgumentProvider {
            ideaHomePath.orNull?.let { listOf("-Didea.home.path=$it") } ?: emptyList()
        }
        // Start a new instance for every test suite
        forkEvery = 1
        // Workaround for the test framework, see https://youtrack.jetbrains.com/issue/IDEA-278926
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*JML*Test.class")
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

// Configure UI tests plugin
// Read more: https://github.com/JetBrains/intellij-ui-test-robot
intellijPlatformTesting.runIde.register("runIdeForUiTests") {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
            )
        }
    }

    plugins {
        robotServerPlugin()
    }
}
