/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.util.*
import kotlin.collections.HashMap

/**
 * The name or alternative names can be used in gradle.properties of the modules (in arbitrary case).
 * That means we need to be careful if/when renaming or deleting any enum value or its name.
 */
enum class ComposePlatforms(vararg val alternativeNames: String) {
    KotlinMultiplatform("Common"),
    Desktop("Jvm"),
    AndroidRelease("Android"),
    Js("Web"),
    WasmJs("Web"),
    MacosX64("Macos"),
    MacosArm64("Macos"),
    IosX64("UiKit"),
    IosArm64("UiKit"),
    IosSimulatorArm64("UiKit"),
    TvosArm64("TvOs"),
    TvosX64("TvOs"),
    TvosSimulatorArm64("TvOs"),
    WatchosArm64("WatchOs"),
    WatchosArm32("WatchOs"),
    WatchosX64("WatchOs"),
    WatchosSimulatorArm64("WatchOs"),
    LinuxX64("Linux"),
    LinuxArm64("Linux"),
    MingwX64("Mingw"),
    OhosArm64("Ohos")
    ;

    private val namesLowerCased by lazy {
        listOf(name, *alternativeNames).map { it.lowercase() }.toSet()
    }

    fun nameContainedByIgnoringCase(nameToMatch: String): Boolean {
        val nameToMatchInLowerCase = nameToMatch.lowercase()
        return namesLowerCased.any { nameToMatchInLowerCase.contains(it) }
    }

    fun matchesAnyIgnoringCase(namesToMatch: Collection<String>): Boolean {
        val namesToMatchLowerCased = namesToMatch.map { it.lowercase() }.toSet()
        return namesToMatchLowerCased.intersect(this.namesLowerCased).isNotEmpty()
    }

    fun matches(nameCandidate: String): Boolean =
        listOf(name, *alternativeNames).any { it.equals(nameCandidate, ignoreCase = true) }

    companion object {
        val ALL = EnumSet.allOf(ComposePlatforms::class.java)

        val JVM_BASED = EnumSet.of(
            ComposePlatforms.Desktop,
            ComposePlatforms.AndroidRelease
        )

        val IOS = EnumSet.of(
            ComposePlatforms.IosX64,
            ComposePlatforms.IosArm64,
            ComposePlatforms.IosSimulatorArm64
        )

        val ANDROID = EnumSet.of(
            ComposePlatforms.AndroidRelease
        )

        // These platforms are not supported by skiko yet
        val NO_SKIKO = EnumSet.of(
            ComposePlatforms.TvosArm64,
            ComposePlatforms.TvosX64,
            ComposePlatforms.TvosSimulatorArm64,
            ComposePlatforms.WatchosArm64,
            ComposePlatforms.WatchosArm32,
            ComposePlatforms.WatchosX64,
            ComposePlatforms.WatchosSimulatorArm64,
            ComposePlatforms.LinuxX64,
            ComposePlatforms.LinuxArm64,
            ComposePlatforms.MingwX64,
        )

        /**
         * Maps comma separated list of platforms into a set of [ComposePlatforms]
         * The function is case- and whitespace-insensetive.
         *
         * Special value: all
         */
        fun parse(platformsNames: String): Set<ComposePlatforms> {
            val platforms = EnumSet.noneOf(ComposePlatforms::class.java)
            val unknownNames = arrayListOf<String>()

            for (name in platformsNames.split(",").map { it.trim() }.filter { it.isNotBlank() }) {
                if (name.equals("all", ignoreCase = true)) {
                    return ALL
                }

                val matchingPlatforms = ALL.filter { it.matches(name) }
                if (matchingPlatforms.isNotEmpty()) {
                    platforms.addAll(matchingPlatforms)
                } else {
                    unknownNames.add(name)
                }
            }

            if (unknownNames.isNotEmpty()) {
                error("Unknown platforms: ${unknownNames.joinToString(", ")}")
            }

            return platforms
        }

        fun handleBootstrapComposeVersion(
            project: Project,
            publishingPlatforms: Set<ComposePlatforms>,
            bootstrapVersion: String? = null
        ) {
            if (publishingPlatforms.isEmpty()) return
            val selectedBootstrapVersion = bootstrapVersion
                ?: project.findProperty("compose.bootstrap.version")?.toString()
                ?: "1.6.1"

            val enabledConfigurations = HashMap<Configuration, Boolean>()
            for (configuration in project.configurations) {
                for (platform in publishingPlatforms) {
                    if (configuration.name.contains("common", true) ||
                        platform.nameContainedByIgnoringCase(configuration.name)) {
                        enabledConfigurations[configuration] = true
                        for (superConfiguration in configuration.hierarchy) {
                            enabledConfigurations[superConfiguration] = true
                        }
                    } else {
                        enabledConfigurations.putIfAbsent(configuration, false)
                    }
                }
            }
            for (entry in enabledConfigurations.entries) {
                if (!entry.value) {
                    entry.key.resolutionStrategy {
                        eachDependency {
                            if (requested.group.startsWith("org.jetbrains.compose") && !requested.group.endsWith("compiler")) {
                                useVersion(selectedBootstrapVersion)
                            }
                        }
                    }
                }
            }
        }
    }
}
