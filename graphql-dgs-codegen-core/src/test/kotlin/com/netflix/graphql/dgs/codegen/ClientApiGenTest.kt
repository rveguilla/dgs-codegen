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

package com.netflix.graphql.dgs.codegen

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.ParameterizedTypeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.JavaFileObject

class ClientApiGenTest {

    val basePackageName = "com.netflix.graphql.dgs.codegen.tests.generated"

    @Test
    fun generateQueryType() {

        val schema = """
            type Query {
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("PeopleGraphQLQuery")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateQueryTypeWithComments() {

        val schema = """
            type Query {
                ""${'"'}
                All the people
                ""${'"'}
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("PeopleGraphQLQuery")
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.javadoc.toString()).isEqualTo(
            """
            All the people
            """.trimIndent()
        )

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateMutationType() {

        val schema = """
            type Mutation {
                updateMovie(movieId: ID, title: String): Movie
            }
            
            type Movie {
                movieId: ID
                title: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("UpdateMovieGraphQLQuery")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateMutationWithInputType() {

        val schema = """
            type Mutation {
                updateMovie(movie: MovieDescription): Movie
            }
            
            input MovieDescription {
                movieId: ID
                title: String
                actors: [String]
            }
            
            type Movie {
                movieId: ID
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("UpdateMovieGraphQLQuery")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes))
    }

    @Test
    fun generateMutationWithInputDescription() {

        val schema = """
            type Mutation {
                updateMovie(
                ""${'"'}
                Some movie description
                ""${'"'}
                movie: MovieDescription): Movie
            }
            
            input MovieDescription {
                movieId: ID
                title: String
                actors: [String]
            }
            
            type Movie {
                movieId: ID
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                writeToFiles = true
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.typeSpecs[0].methodSpecs[1].javadoc.toString()).isEqualTo("Some movie description")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes))
    }

    @Test
    fun generateRecursiveInputTypes() {

        val schema = """
            type Query {
                movies(filter: MovieQuery): [String]
            }
            
            input MovieQuery {
                booleanQuery: BooleanQuery!
                titleFilter: String
            }
            
            input BooleanQuery {
                first: MovieQuery!
                second: MovieQuery!
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateDataTypes = false,
                generateClientApi = true,
                includeQueries = mutableSetOf("movies")
            )
        ).generate()

        assertThat(codeGenResult.javaDataTypes.size).isEqualTo(2)
        assertThat(codeGenResult.javaDataTypes[0].typeSpec.name).isEqualTo("MovieQuery")
        assertThat(codeGenResult.javaDataTypes[1].typeSpec.name).isEqualTo("BooleanQuery")

        assertCompilesJava(codeGenResult.javaDataTypes)
    }

    @Test
    fun generateMutationAddsNullChecksDuringInit() {

        val schema = """
            type Mutation {
                updateMovie(movie: MovieDescription, reviews: [String], uuid: UUID): Movie
            }
            
            input MovieDescription {
                movieId: Int
                title: String
                actors: [String]
            }
            
            type Movie {
                movieId: Int
                lastname: String
            }
            
            scalar UUID @javaType(name : "java.util.UUID")
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        val initMethod = codeGenResult.javaQueryTypes[0].typeSpec.methodSpecs
            .find { it.name == "<init>" }?.code.toString()

        assert(
            initMethod.contains(
                "super(\"mutation\");\n" +
                    "if (movie != null || fieldsSet.contains(\"movie\")) {\n" +
                    "    getInput().put(\"movie\", movie);\n" +
                    "}if (reviews != null || fieldsSet.contains(\"reviews\")) {\n" +
                    "    getInput().put(\"reviews\", reviews);\n" +
                    "}if (uuid != null || fieldsSet.contains(\"uuid\")) {\n" +
                    "    getInput().put(\"uuid\", uuid);\n" +
                    "}"
            )
        )
        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes))
    }

    @Test
    fun generateMutationDoesNotAddNullChecksForPrimitiveTypesDuringInit() {

        val schema = """
            type Mutation {
                updateMovie(movieId: Int!): Movie
            }
            
            type Movie {
                movieId: Int
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true
            )
        ).generate()

        assert(
            codeGenResult.javaQueryTypes[0].typeSpec.methodSpecs
                .find { it.name == "<init>" }?.code.toString()
                .contains("super(\"mutation\");\ngetInput().put(\"movieId\", movieId);")
        )

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes))
    }

    @Test
    fun generateProjectionRoot() {

        val schema = """
            type Query {
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(1)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("PeopleProjectionRoot")

        assertCompilesJava(codeGenResult.clientProjections)
    }

    @Test
    fun generateProjectionRootTestWithCycles() {

        val schema = """
            type Query @extends {
                persons: [Person]
            }

            type Person {
             name: String
             friends: [Person]
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()
        assertThat(codeGenResult.clientProjections.size).isEqualTo(2)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("PersonsProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Persons_FriendsProjection")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs).extracting("name").contains("name")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs).extracting("name").contains("friends")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").contains("name")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateInterfaceProjectionsWithCycles() {
        val schema = """
            type Query {
                search(title: String): [Show]
            }
            
            interface Show {
                title: String
            }
            
            type Movie implements Show {
                title: String
                duration: Int
                details: Details
            }
            
            type Details {
                 show: Show
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(5)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_Movie_DetailsProjection")
        assertThat(codeGenResult.clientProjections[3].typeSpec.name).isEqualTo("Search_Movie_Details_ShowProjection")
        assertThat(codeGenResult.clientProjections[4].typeSpec.name).isEqualTo("Search_Movie_Details_Show_MovieProjection")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun generateUnionProjectionsWithCycles() {
        val schema = """
            type Query {
                search(title: String): [Video]
            }
            
            union Video = Show | Movie
            
            type Show {
                title: String
            }
            
            type Movie {
                title: String
                duration: Int
                related: Related
            }
            
            type Related {
                 video: Video
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(CodeGenConfig(schemas = setOf(schema), packageName = basePackageName, generateClientApi = true)).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(7)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_ShowProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(codeGenResult.clientProjections[3].typeSpec.name).isEqualTo("Search_Movie_RelatedProjection")
        assertThat(codeGenResult.clientProjections[4].typeSpec.name).isEqualTo("Search_Movie_Related_VideoProjection")
        assertThat(codeGenResult.clientProjections[5].typeSpec.name).isEqualTo("Search_Movie_Related_Video_ShowProjection")
        assertThat(codeGenResult.clientProjections[6].typeSpec.name).isEqualTo("Search_Movie_Related_Video_MovieProjection")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun generateSubProjectionsWithDifferentRootTypes() {

        val schema = """
            type Query @extends {
                persons: [Person]
                friends: [Person]
            }

            type Person {
             name: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("PersonsProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("FriendsProjectionRoot")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateSubProjectionsWithDifferentParentTypes() {

        val schema = """
            type Query @extends {
                persons: [Person]
                details(name: String): Details
            }

            type Person {
               details: Details
            }
            
            type Details {
                name: String
                age: Integer
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("PersonsProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Persons_DetailsProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("DetailsProjectionRoot")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateSubProjectionTypes() {

        val schema = """
            type Query {
                movies: [Movie]
            }
            
            type Movie {
                title: String
                actors: [Actor]
                type: MovieType
            }
            
            type Actor {
                name: String
                age: Integer
            }
           
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(2)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("MoviesProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Movies_ActorsProjection")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateSubProjectionTypesWithSimilarQueryAndFieldNames() {

        val schema = """
            type Query {
                user: User
            }

            type User {
                favoriteMovie: Movie
                favoriteMovieGenre: Genre
            }

            type Movie {
                genre: Genre
            }

            type Genre {
                name: String
            }      
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(4)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("UserProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("User_FavoriteMovieProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("User_FavoriteMovie_GenreProjection")
        assertThat(codeGenResult.clientProjections[3].typeSpec.name).isEqualTo("User_FavoriteMovieGenreProjection")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateSubProjectionTypesWithShortNames() {

        val schema = """
            type Query {
                movies: [Movie]
            }
            
            type Movie {
                title: String
                actors: [Actor]
            }
            
            type Actor {
                name: String
                age: Integer
                movies: [Movie]
            }

        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                shortProjectionNames = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(3)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("MoviesProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Movies_ActorsProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Mo_Ac_MoviesProjection")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateArgumentsForSimpleTypes() {

        val schema = """
            type Query {
                personSearch(lastname: String): [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }

        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.typeSpecs[0].methodSpecs[1].name).isEqualTo("lastname")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateArgumentsForEnum() {

        val schema = """
            type Query {
                personSearch(index: SearchIndex): [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
            
            enum SearchIndex {
                TEST, PROD
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.typeSpecs[0].methodSpecs[1].name).isEqualTo("index")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes))
    }

    @Test
    fun generateArgumentsForObjectType() {

        val schema = """
            type Query {
                personSearch(index: SearchIndex): [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
            
            type SearchIndex {
                name: String
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("PersonSearchGraphQLQuery")
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.typeSpecs[0].methodSpecs[1].name).isEqualTo("index")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes))
    }

    @Test
    fun skipCodegen() {

        val schema = """
            type Query {
                persons: [Person]
                personSearch(index: SearchIndex): [Person] @skipcodegen
            }
            
            type Person {
                firstname: String
                lastname: String
            }
            
            type SearchIndex {
                name: String
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("PersonsGraphQLQuery")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes))
    }

    @Test
    fun interfaceReturnTypes() {
        val schema = """
            type Query {
                search(title: String): [Show]
            }
            
            interface Show {
                title: String
            }
            
            type Movie implements Show {
                title: String
                duration: Int
            }
            
            type Series implements Show {
                title: String
                episodes: Int
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("SearchGraphQLQuery")
        assertThat(codeGenResult.clientProjections.size).isEqualTo(3)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs[0].name).isEqualTo("title")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs[1].name).isEqualTo("duration")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_SeriesProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs[1].name).isEqualTo("episodes")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun interfaceFragment() {
        val schema = """
            type Query {
                search(title: String): [Show]
            }
            
            interface Show {
                title: String
            }
            
            type Movie implements Show {
                title: String
                duration: Int
            }
            
            type Series implements Show {
                title: String
                episodes: Int
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(3)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs[0].name).isEqualTo("title")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").contains("duration")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").doesNotContain("title")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").doesNotContain("episodes")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_SeriesProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").contains("episodes")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").doesNotContain("title")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").doesNotContain("duration")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun interfaceFragmentOnSubType() {
        val schema = """
            type Query {
                search(title: String): [Result]
            }
            
            type Result {
                show: Show
            }
            
            interface Show {
                title: String
            }
            
            type Movie implements Show {
                title: String
                duration: Int
            }
            
            type Series implements Show {
                title: String
                episodes: Int
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(4)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_ShowProjection")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").contains("title")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_Show_MovieProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").contains("duration")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").doesNotContain("title")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").doesNotContain("episodes")
        assertThat(codeGenResult.clientProjections[3].typeSpec.name).isEqualTo("Search_Show_SeriesProjection")
        assertThat(codeGenResult.clientProjections[3].typeSpec.methodSpecs).extracting("name").contains("episodes")
        assertThat(codeGenResult.clientProjections[3].typeSpec.methodSpecs).extracting("name").doesNotContain("title")
        assertThat(codeGenResult.clientProjections[3].typeSpec.methodSpecs).extracting("name").doesNotContain("duration")

        val superclass = codeGenResult.clientProjections[3].typeSpec.superclass as ParameterizedTypeName
        assertThat(superclass.typeArguments[1]).extracting("simpleName").containsExactly("SearchProjectionRoot")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun unionFragment() {
        val schema = """
            type Query {
                search: [Result]
            }
            
            union Result = Movie | Actor

            type Movie {
                title: String
            }

            type Actor {
                name: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(3)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs).extracting("name").contains("onMovie")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs).extracting("name").contains("onActor")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").contains("title")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").doesNotContain("name")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_ActorProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").contains("name")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").doesNotContain("title")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun unionFragmentOnSubType() {
        val schema = """
            type Query {
                search(title: String): [Result]
            }
            
            type Result {
                result: SearchResult
            }
            
            union SearchResult = Movie | Actor

            type Movie {
                title: String
            }

            type Actor {
                name: String
            }
            
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(4)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("SearchProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Search_ResultProjection")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").doesNotContain("title")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").doesNotContain("name")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").contains("onMovie")
        assertThat(codeGenResult.clientProjections[1].typeSpec.methodSpecs).extracting("name").contains("onActor")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Search_Result_MovieProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").contains("title")
        assertThat(codeGenResult.clientProjections[2].typeSpec.methodSpecs).extracting("name").doesNotContain("name")
        assertThat(codeGenResult.clientProjections[3].typeSpec.name).isEqualTo("Search_Result_ActorProjection")
        assertThat(codeGenResult.clientProjections[3].typeSpec.methodSpecs).extracting("name").contains("name")
        assertThat(codeGenResult.clientProjections[3].typeSpec.methodSpecs).extracting("name").doesNotContain("title")

        assertThat(codeGenResult.clientProjections[2].typeSpec.initializerBlock.isEmpty).isFalse()
        assertThat(codeGenResult.clientProjections[3].typeSpec.initializerBlock.isEmpty).isFalse()

        val superclass = codeGenResult.clientProjections[3].typeSpec.superclass as ParameterizedTypeName
        assertThat(superclass.typeArguments[1]).extracting("simpleName").containsExactly("SearchProjectionRoot")

        val searchResult = codeGenResult.javaInterfaces[0].typeSpec

        Truth.assertThat(JavaFile.builder("$basePackageName.types", searchResult).build().toString()).isEqualTo(
            """
                |package com.netflix.graphql.dgs.codegen.tests.generated.types;
                |
                |import com.fasterxml.jackson.annotation.JsonSubTypes;
                |import com.fasterxml.jackson.annotation.JsonTypeInfo;
                |
                |@JsonTypeInfo(
                |    use = JsonTypeInfo.Id.NAME,
                |    include = JsonTypeInfo.As.PROPERTY,
                |    property = "__typename"
                |)
                |@JsonSubTypes({
                |    @JsonSubTypes.Type(value = Movie.class, name = "Movie"),
                |    @JsonSubTypes.Type(value = Actor.class, name = "Actor")
                |})
                |public interface SearchResult {
                |}
                |""".trimMargin()
        )

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaEnumTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaInterfaces))
    }

    @Test
    fun testScalarsDontGenerateProjections() {
        val schema = """
          type Query {
              movieCountry: MovieCountry
          }
          
          type MovieCountry {
            country: String
            movieId: Long
          }
          scalar Long

        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                typeMapping = mapOf(Pair("Long", "java.lang.Long")),
            )
        ).generate()
        val projections = codeGenResult.clientProjections
        assertThat(projections.size).isEqualTo(1)
        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaEnumTypes))
    }

    @Test
    fun testExtendRootProjection() {
        val schema = """
          type Query {
              people: [Person]
          }
          
          type Person {
            name: String
          }
          
          extend type Person {
            email: String
          }

        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                typeMapping = mapOf(Pair("Long", "java.lang.Long")),
            )
        ).generate()
        val projections = codeGenResult.clientProjections
        assertThat(projections.size).isEqualTo(1)
        assertThat(projections[0].typeSpec.name).isEqualTo("PeopleProjectionRoot")
        assertThat(projections[0].typeSpec.methodSpecs.size).isEqualTo(2)
        assertThat(projections[0].typeSpec.methodSpecs).extracting("name").containsExactly("name", "email")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaEnumTypes))
    }

    @Test
    fun testExtendSubProjection() {
        val schema = """
          type Query {
            search: [SearchResult]
          }
          
          type SearchResult {
            movie: Movie
          }
          
          type Movie {
            title: String
          }
          
          extend type Movie {
            director: String
          }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                typeMapping = mapOf(Pair("Long", "java.lang.Long")),
            )
        ).generate()
        val projections = codeGenResult.clientProjections
        assertThat(projections.size).isEqualTo(2)
        assertThat(projections[1].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(projections[1].typeSpec.methodSpecs.size).isEqualTo(3)
        assertThat(projections[1].typeSpec.methodSpecs).extracting("name").contains("title", "director", "<init>")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaEnumTypes))
    }

    @Test
    fun testExtendSubProjectionOutOfOrder() {
        val schema = """
          type Query {
            search: [SearchResult]
          }

          type SearchResult {
            movie: Movie
          }

          extend type Movie {
            director: String
          }

          type Movie {
            title: String
          }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                typeMapping = mapOf(Pair("Long", "java.lang.Long")),
            )
        ).generate()
        val projections = codeGenResult.clientProjections
        assertThat(projections.size).isEqualTo(2)
        assertThat(projections[1].typeSpec.name).isEqualTo("Search_MovieProjection")
        assertThat(projections[1].typeSpec.methodSpecs.size).isEqualTo(3)
        assertThat(projections[1].typeSpec.methodSpecs).extracting("name").contains("title", "director", "<init>")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes).plus(codeGenResult.javaDataTypes).plus(codeGenResult.javaEnumTypes))
    }

    @Test
    fun includeQueryConfig() {

        val schema = """
            type Query {
                movieTitles: [String]
                actorNames: [String]
            }           
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                includeQueries = setOf("movieTitles"),
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("MovieTitlesGraphQLQuery")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun includeMutationConfig() {

        val schema = """
            type Mutation {
                updateMovieTitle: String
                addActorName: Boolean
            }           
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                includeMutations = setOf("updateMovieTitle"),
            )
        ).generate()

        assertThat(codeGenResult.javaQueryTypes.size).isEqualTo(1)
        assertThat(codeGenResult.javaQueryTypes[0].typeSpec.name).isEqualTo("UpdateMovieTitleGraphQLQuery")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun generateProjectionRootWithReservedNames() {

        val schema = """
            type Query {
                weirdType: WeirdType
            }
            
            type WeirdType {
                _: String
                root: String
                parent: String
                import: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(1)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("WeirdTypeProjectionRoot")
        assertThat(codeGenResult.clientProjections[0].typeSpec.methodSpecs).extracting("name").containsExactly("__", "_root", "_parent", "_import")

        assertCompilesJava(codeGenResult.clientProjections)
    }

    @Test
    fun generateSubProjectionWithReservedNames() {

        val schema = """
            type Query {
                normalType: NormalType
            }
            
            type NormalType {
                weirdType: WeirdType
            }
            
            type WeirdType {
                _: String
                root: String
                parent: String
                import: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(2)
        val weirdType = codeGenResult.clientProjections.find { it.typeSpec.name == "NormalType_WeirdTypeProjection" }

        assertThat(weirdType?.typeSpec?.methodSpecs).extracting("name").contains("__", "_root", "_parent", "_import")

        assertCompilesJava(codeGenResult.clientProjections)
    }

    @Test
    fun generateProjectionsForSameTypeInSameQueryWithDifferentPaths() {
        val schema = """
            type Query {
                workshop: Workshop
            }
            
            type Workshop {
                reviews: ReviewConnection
                assets: Asset
            }
            
            type ReviewConnection {
                edges: [ReviewEdge]
            }
            
            type ReviewEdge {
                node: String
            }
            
            type Asset {
                reviews: ReviewConnection          
            }                     
        """.trimIndent()

        val codeGenResult = CodeGen(CodeGenConfig(schemas = setOf(schema), packageName = basePackageName, generateClientApi = true, writeToFiles = false)).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(6)
        val workshopAssetsReviewsProjection = codeGenResult.clientProjections.find { it.typeSpec.name == "Workshop_Assets_ReviewsProjection" }!!
        val workshopReviewsProjection = codeGenResult.clientProjections.find { it.typeSpec.name == "Workshop_ReviewsProjection" }!!

        assertThat(workshopReviewsProjection.typeSpec.methodSpecs).extracting("name").contains("edges")
        assertThat(workshopAssetsReviewsProjection.typeSpec.methodSpecs).extracting("name").contains("edges")
    }

    @Test
    fun generateOnlyRequiredDataTypesForQuery() {
        val schema = """
            type Query {
                shows(showFilter: ShowFilter): [Show]
                people(personFilter: PersonFilter): [Person]
            }
            
            type Show {
                title: String
            }
            
            enum ShouldNotInclude {
                YES,NO
            }
            
            input NotUsed {
                field: String
            }
            
            input ShowFilter {
                title: String
                showType: ShowType
                similarTo: SimilarityInput              
            }
            
            input SimilarityInput {
                tags: [String]
            }
            
            enum ShowType {
                MOVIE, SERIES
            }
            
            type Person {
                name: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(CodeGenConfig(schemas = setOf(schema), packageName = basePackageName, generateClientApi = true, includeQueries = setOf("shows"), generateDataTypes = false, writeToFiles = false)).generate()
        assertThat(codeGenResult.javaDataTypes.size).isEqualTo(2)
        assertThat(codeGenResult.javaDataTypes).extracting("typeSpec").extracting("name").containsExactly("ShowFilter", "SimilarityInput")
        assertThat(codeGenResult.javaEnumTypes).extracting("typeSpec").extracting("name").containsExactly("ShowType")

        assertCompilesJava(codeGenResult.clientProjections + codeGenResult.javaDataTypes + codeGenResult.javaEnumTypes)
    }

    @Test
    fun generateOnlyRequiredDataTypesForMutation() {
        val schema = """
            type Mutation {
                shows(showFilter: ShowFilter): [Show]
                people(personFilter: PersonFilter): [Person]
            }
            
            type Show {
                title: String
            }
            
            enum ShouldNotInclude {
                YES,NO
            }
            
            input NotUsed {
                field: String
            }
            
            input ShowFilter {
                title: String
                showType: ShowType
                similarTo: SimilarityInput              
            }
            
            input SimilarityInput {
                tags: [String]
            }
            
            enum ShowType {
                MOVIE, SERIES
            }
            
            type Person {
                name: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(CodeGenConfig(schemas = setOf(schema), packageName = basePackageName, generateClientApi = true, includeMutations = setOf("shows"), generateDataTypes = false, writeToFiles = false)).generate()
        assertThat(codeGenResult.javaDataTypes.size).isEqualTo(2)
        assertThat(codeGenResult.javaDataTypes).extracting("typeSpec").extracting("name").containsExactly("ShowFilter", "SimilarityInput")
        assertThat(codeGenResult.javaEnumTypes).extracting("typeSpec").extracting("name").containsExactly("ShowType")

        assertCompilesJava(codeGenResult.clientProjections + codeGenResult.javaDataTypes + codeGenResult.javaEnumTypes)
    }

    @Test
    fun generateSubProjectionTypesMaxDepth() {

        val schema = """
            type Query {
                movies: [Movie]
            }
            
            type Movie {
                title: String
                rating: Rating
                actors: [Actor]
            }
            
            type Actor {
                name: String
                age: Integer
                agent: Agent
            }
            
            type Agent {
                name: String  
                address : Address
            }
            
            type Address {
                street: String
            }
            
            type Rating {
                starts: Integer
                review: Review 
            }
            
            type Review {
                description: String
            }
                

        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                maxProjectionDepth = 2,
            )
        ).generate()

        assertThat(codeGenResult.clientProjections.size).isEqualTo(5)
        assertThat(codeGenResult.clientProjections[0].typeSpec.name).isEqualTo("MoviesProjectionRoot")
        assertThat(codeGenResult.clientProjections[1].typeSpec.name).isEqualTo("Movies_RatingProjection")
        assertThat(codeGenResult.clientProjections[2].typeSpec.name).isEqualTo("Movies_Rating_ReviewProjection")
        assertThat(codeGenResult.clientProjections[3].typeSpec.name).isEqualTo("Movies_ActorsProjection")
        assertThat(codeGenResult.clientProjections[4].typeSpec.name).isEqualTo("Movies_Actors_AgentProjection")

        assertCompilesJava(codeGenResult.clientProjections.plus(codeGenResult.javaQueryTypes))
    }

    @Test
    fun `Fields explicitly set to null in the builder should be included`() {
        val schema = """
            type Query {
                filter(nameFilter: String): [String]
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                maxProjectionDepth = 2,
            )
        ).generate()

        val builderClass = compileAndGetClass(codeGenResult.javaQueryTypes, "FilterGraphQLQuery\$Builder")
        val buildMethod = builderClass.getMethod("build")
        val nameMethod = builderClass.getMethod("nameFilter", String::class.java)

        // When the 'nameFilter' method is invoked with a null value, the field should be included in the input map and explicitly set to null.
        val builder1 = builderClass.constructors[0].newInstance()
        nameMethod.invoke(builder1, null)
        val resultQueryObject: GraphQLQuery = buildMethod.invoke(builder1) as GraphQLQuery
        assertThat(resultQueryObject.input.keys).containsExactly("nameFilter")
        assertThat(resultQueryObject.input["nameFilter"]).isNull()
    }

    @Test
    fun `Fields not explicitly set to null or any value in the builder should not be included`() {
        val schema = """
            type Query {
                filter(nameFilter: String): [String]
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = basePackageName,
                generateClientApi = true,
                maxProjectionDepth = 2,
            )
        ).generate()

        val builderClass = compileAndGetClass(codeGenResult.javaQueryTypes, "FilterGraphQLQuery\$Builder")
        val buildMethod = builderClass.getMethod("build")

        // When the 'nameFilter' method is not invoked, it should not be included in the input map.
        val builder2 = builderClass.constructors[0].newInstance()
        val result2QueryObject: GraphQLQuery = buildMethod.invoke(builder2) as GraphQLQuery
        assertThat(result2QueryObject.input.keys).isEmpty()
        assertThat(result2QueryObject.input["nameFilter"]).isNull()
    }

    private fun compileAndGetClass(dataTypes: List<JavaFile>, type: String): Class<*> {
        val packageNameAsUnixPath = basePackageName.replace(".", "/")
        val compilation = assertCompilesJava(dataTypes)
        val temporaryFilesystem = Jimfs.newFileSystem(Configuration.unix())
        val classpath = compilation.generatedFiles()
            .filter { it.kind == JavaFileObject.Kind.CLASS }
            .map {
                val destFile = temporaryFilesystem.getPath(it.toUri().path)
                Files.createDirectories(destFile.parent)
                it.openInputStream().use { input ->
                    Files.newOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.parent
            }
            .toSet()
            .map { URL(it.toUri().toString().replace("$packageNameAsUnixPath.*".toRegex(), "")) }
            .toTypedArray()

        return URLClassLoader(classpath).loadClass("$basePackageName.client.$type")
    }
}
