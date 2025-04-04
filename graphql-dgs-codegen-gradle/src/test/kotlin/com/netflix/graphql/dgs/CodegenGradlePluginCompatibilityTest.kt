/*
 *
 *  Copyright 2020 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.netflix.graphql.dgs

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/**
 * Test against different Gradle Versions. Since the intention is to test against different **Gradle versions**
 * this test should **not** attempt to be a measure of correctness to all the configuration permutations that the
 * plugin can have, It should only include features that are relevant to assert the plugin works well from one
 * Gradle versions vs another.
 * */
class CodegenGradlePluginCompatibilityTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @ValueSource(strings = ["8.6", "8.7", "8.8"])
    fun `Test generateJava against multiple Gradle Versions`(gradleVersion: String) {
        prepareBuildGraphQLSchema(
            """
                 type Query {
                     movies(from: Int, to: Int, movieIds: [Int]): [Movie]
                 }
                 
                 type Movie {
                     movieId: ID!
                     title: String
                     tags(from: Int, to: Int, sourceType: SourceType): [MovieTag]
                     isLive(countryFilter: CountryFilter): Boolean
                 }
                 
                 input CountryFilter {
                    countriesToExclude: [String]
                 }
                 
                type MovieTag {
                     movieId: Long
                     tagId: Long
                     sourceType: SourceType
                     tagValues(from: Int, to: Int): [String]
                 } 
                 
                 enum SourceType {
                   FOO
                   BAR
                 }
            """.trimMargin(),
        )

        prepareBuildGradleFile(
            """
                plugins {
                    id 'java'
                    id 'com.netflix.dgs.codegen'
                }
                
                 repositories {
                	mavenCentral()
                }

                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(17))
                    }
                }

                 generateJava {
                     packageName = 'com.netflix.testproject.graphql'
                     generateClient = true
                     typeMapping = [
                        Long:   "java.lang.Long",
                     ]
                }

                // Need to disable the core conventions since the artifacts are not yet visible.
                codegen.clientCoreConventionsEnabled = false
            """.trimMargin(),
        )

        val result =
            GradleRunner
                .create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments(
                    "--stacktrace",
                    "--info",
                    "generateJava",
                    "build",
                ).build()

        assertThat(result.task(":generateJava")).extracting { it?.outcome }.isEqualTo(SUCCESS)
        assertThat(result.task(":build")).extracting { it?.outcome }.isEqualTo(SUCCESS)
    }

    private fun prepareBuildGradleFile(content: String) {
        writeProjectFile("build.gradle", content)
    }

    private fun prepareBuildGraphQLSchema(content: String) {
        writeProjectFile("src/main/resources/schema/schema.graphql", content)
    }

    private fun writeProjectFile(
        relativePath: String,
        content: String,
    ) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}
