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

import com.mongodb.ConnectionString
import com.mongodb.async.SingleResultCallback
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION
import de.flapdoodle.embed.process.runtime.Network
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document

/**
 *
 */
internal object ReplicaSetEmbeddedMongo {

    var ports = Network.getFreeServerPorts(Network.getLocalHost(), 3)

    val rep1: IMongodConfig = MongodConfigBuilder()
        .version(PRODUCTION)
        .net(Net(ports[0], Network.localhostIsIPv6()))
        .withLaunchArgument("--replSet", "kmongo")
        .cmdOptions(
            MongoCmdOptionsBuilder()
                .useSmallFiles(true)
                .build()
        )
        .build()
    val rep2: IMongodConfig = MongodConfigBuilder()
        .version(PRODUCTION)
        .net(Net(ports[1], Network.localhostIsIPv6()))
        .withLaunchArgument("--replSet", "kmongo")
        .cmdOptions(
            MongoCmdOptionsBuilder()
                .useSmallFiles(true)
                .build()
        )
        .build()
    val rep3: IMongodConfig = MongodConfigBuilder()
        .version(PRODUCTION)
        .net(Net(ports[2], Network.localhostIsIPv6()))
        .withLaunchArgument("--replSet", "kmongo")
        .cmdOptions(
            MongoCmdOptionsBuilder()
                .useSmallFiles(true)
                .build()
        )
        .build()


    private val mongodProcesses: List<MongodProcess> by lazy {
        createInstance()
    }

    fun connectionString(commandExecutor: (String, BsonDocument, SingleResultCallback<Document>) -> Unit): ConnectionString {
        val host = mongodProcesses[0].host
        val conf = BsonDocument("_id", BsonString("kmongo"))
            .apply {
                put("version", BsonInt32(1))
                put(
                    "members",
                    BsonArray(
                        mongodProcesses.mapIndexed { i, p ->
                            val s = BsonDocument("_id", BsonInt32(i))
                            s.put("host", BsonString(p.host))
                            s
                        })
                )
            }
        val initCommand = BsonDocument("replSetInitiate", conf)
        val reconfigCommand = BsonDocument("replSetReconfig", conf).apply {
            put("force", BsonBoolean(true))
        }


        try {
            fun reconfigCallback(first: Boolean = false): SingleResultCallback<Document> =
                SingleResultCallback { result, t ->
                    if (first || t != null) {
                        Thread.sleep(100)
                        commandExecutor.invoke(host, reconfigCommand, reconfigCallback())
                    }
                }
            commandExecutor.invoke(
                host,
                initCommand,
                SingleResultCallback { result, t ->
                    reconfigCallback(true)
                }
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mongodProcesses.run {
            ConnectionString(
                "mongodb://${first().host},${get(1).host},${get(2).host}/?replicaSet=kmongo"
            )
        }
    }

    private fun createInstance(): List<MongodProcess> =
        listOf(
            MongodStarter.getDefaultInstance().prepare(rep1).start(),
            MongodStarter.getDefaultInstance().prepare(rep2).start(),
            MongodStarter.getDefaultInstance().prepare(rep3).start()
        )

}