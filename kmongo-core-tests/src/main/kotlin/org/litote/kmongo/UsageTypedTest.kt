/*
 * Copyright (C) 2017 Litote
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litote.kmongo

import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.litote.kmongo.UsageTypedTest.Jedi
import org.litote.kmongo.model.Friend
import java.time.LocalDate
import java.time.Month
import kotlin.test.assertEquals

/**
 *
 */
class UsageTypedTest : KMongoBaseTest<Jedi>() {

    data class Jedi(val name: String, val age: Int, val firstAppearance: StarWarsFilm)
    data class StarWarsFilm(val name: String, val date: LocalDate)

    data class LightSaber1(val _id: String?)
    data class LightSaber2(val _id: org.bson.types.ObjectId?)
    data class LightSaber3(val _id: String)
    data class LightSaber4(val _id: org.bson.types.ObjectId)
    data class LightSaber5(var _id: String?)
    data class LightSaber6(var _id: org.bson.types.ObjectId?)

    class TFighter(val version: String, val pilot: Pilot?)
    class Pilot()

    @Before
    fun setup() {
        col.insertOne(Jedi("Luke Skywalker", 19, StarWarsFilm("A New Hope", LocalDate.of(1977, Month.MAY, 25))))
        col.insertOne("{name:'Yoda',age:896,firstAppearance:{name:'The Empire Strikes Back',date:new Date('Sat May 17 1980 00:00:00 CEST')}}")
    }

    @Category(JacksonMappingCategory::class, NativeMappingCategory::class)
    @Test
    fun firstSample() {
        val yoda = col.findOne(Friend::name regex "Yo.*")

        val luke = col.aggregate<Jedi>(
            match(Jedi::age lt yoda?.age),
            sample(1)
        ).first()

        assertEquals("Luke Skywalker", luke?.name)
        assertEquals("Yoda", yoda?.name)
    }

    @Category(JacksonMappingCategory::class)
    @Test
    fun aggregateSample() {
        val (averageAge, maxAge) = col.aggregate<Pair<Int, Int>>(
            group(
                null,
                Pair<Int, Int>::first avg Jedi::age.projection,
                Pair<Int, Int>::second max Jedi::age.projection
            )
        ).first()!!
        assertEquals((896 + 19) / 2, averageAge)
        assertEquals(896, maxAge)
    }

}