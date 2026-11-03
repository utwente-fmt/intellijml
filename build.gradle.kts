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
    // Generates the JML lexer (JFlex) and parser (Grammar-Kit) from src/main/grammar
    id("org.jetbrains.intellij.platform.grammarkit") version "2.19.0"
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

sourceSets["main"].java.srcDirs(tasks.generateLexer, tasks.generateParser)
// The test data in src/test/testData is not compiled: the fixtures copy it into a test project

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    generateLexer {
        sourceFile = layout.projectDirectory.file("src/main/grammar/JML.flex")
        purgeOldFiles = true
    }

    generateParser {
        sourceFile = layout.projectDirectory.file("src/main/grammar/JML.bnf")
        purgeOldFiles = true
    }

    // Emit bytecode for the oldest supported platform (2025.x runs on Java 21)
    withType<JavaCompile> {
        options.release = properties("javaVersion").toInt()
    }

    test {
        // The Java test fixtures use mock JDK 21, which the test framework downloads from Maven on first use
        // (org.jetbrains.mockjdk:mockjdk-base-java into ~/.m2)
        // Start a new instance for every test suite
        forkEvery = 1
        // Workaround for the test framework, see https://youtrack.jetbrains.com/issue/IDEA-278926
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*JML*Test.class")
        // A test JVM that is interrupted part-way reports its current test as skipped and silently drops the
        // rest, while the build still passes. No test is skipped on purpose, so treat a skipped test as a failure.
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null && result.skippedTestCount > 0) {
                    throw GradleException("${result.skippedTestCount} test(s) were skipped, so the test run may " +
                            "have been interrupted; see the test report")
                }
            }
        })
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
