/*
 * Copyright (C) 2016 Litote
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

import com.mongodb.MongoCommandException
import com.mongodb.ReadPreference
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.AggregateIterable
import com.mongodb.client.ChangeStreamIterable
import com.mongodb.client.DistinctIterable
import com.mongodb.client.FindIterable
import com.mongodb.client.ListIndexesIterable
import com.mongodb.client.MapReduceIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.MongoIterable
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.DeleteOptions
import com.mongodb.client.model.FindOneAndDeleteOptions
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexModel
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.InsertOneOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.WriteModel
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.bson.BsonDocument
import org.bson.conversions.Bson
import org.litote.kmongo.util.KMongoUtil
import org.litote.kmongo.util.KMongoUtil.EMPTY_JSON
import org.litote.kmongo.util.KMongoUtil.defaultCollectionName
import org.litote.kmongo.util.KMongoUtil.extractId
import org.litote.kmongo.util.KMongoUtil.filterIdToBson
import org.litote.kmongo.util.KMongoUtil.idFilterQuery
import org.litote.kmongo.util.KMongoUtil.toBson
import org.litote.kmongo.util.KMongoUtil.toBsonList
import org.litote.kmongo.util.KMongoUtil.toBsonModifier
import org.litote.kmongo.util.KMongoUtil.toWriteModel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

//*******
//MongoDatabase extension methods
//*******

/**
 * Returns a [MongoDatabase] with a KMongo codec.
 */
fun MongoDatabase.withKMongo(): MongoDatabase =
    withCodecRegistry(KMongo.configureRegistry(codecRegistry))

/**
 * Gets a collection.
 *
 * @param collectionName the name of the collection to return
 * @param <T>            the default target type of the collection to return
 * @return the collection
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : Any> MongoDatabase.getCollection(collectionName: String): MongoCollection<T> =
    getCollectionOfName(collectionName)

/**
 * Gets a collection.
 *
 * @param collectionName the name of the collection to return
 * @param <T>            the default target type of the collection to return
 * @return the collection
 */
inline fun <reified T : Any> MongoDatabase.getCollectionOfName(collectionName: String): MongoCollection<T> =
    getCollection(collectionName, T::class.java)

/**
 * Gets a collection.
 *
 * @param <T>            the default target type of the collection to return
 *                       - the name of the collection is determined by [defaultCollectionName]
 * @return the collection
 * @see defaultCollectionName
 */
inline fun <reified T : Any> MongoDatabase.getCollection(): MongoCollection<T> =
    getCollection(defaultCollectionName(T::class), T::class.java)

/**
 * Executes the given command in the context of the current database with the given read preference.
 *
 * @param command        the command to be run
 * @param readPreference the {@link com.mongodb.ReadPreference} to be used when executing the command
 * @param <TResult>      the type of the class to use instead of {@code Document}.
 */
inline fun <reified TResult : Any> MongoDatabase.runCommand(command: String, readPreference: ReadPreference): TResult =
    runCommand(toBson(command), readPreference, TResult::class.java)

/**
 * Executes the given command in the context of the current database with the given read preference.
 *
 * @param command        the command to be run
 * @param <TResult>      the type of the class to use instead of {@code Document}.
 */
inline fun <reified TResult : Any> MongoDatabase.runCommand(command: String): TResult =
    runCommand(command, readPreference)


//*******
//MongoCollection extension methods
//*******

/**
 * Returns a [MongoCollection] with a KMongo codec.
 */
fun <T> MongoCollection<T>.withKMongo(): MongoCollection<T> =
    withCodecRegistry(KMongo.configureRegistry(codecRegistry))

/**
 * Create a new MongoCollection instance with a different default class to cast any documents returned from the database into..
 *
 * @param <NewTDocument> the default class to cast any documents returned from the database into.
 * @return a new MongoCollection instance with the different default class
 */
inline fun <reified NewTDocument : Any> MongoCollection<*>.withDocumentClass(): MongoCollection<NewTDocument> =
    withDocumentClass(NewTDocument::class.java)

/**
 * Counts the number of documents in the collection according to the given options.
 *
 * @param filter  the query filter
 * @param options the options describing the count
 *
 * @return the number of documents in the collection
 */
@Deprecated("use countDocuments instead")
fun <T> MongoCollection<T>.count(filter: String, options: CountOptions = CountOptions()): Long =
    count(toBson(filter), options)

/**
 * Counts the number of documents in the collection according to the given options.
 *
 * @param filter  the query filter
 * @param options the options describing the count
 *
 * @return the number of documents in the collection
 */
fun <T> MongoCollection<T>.countDocuments(filter: String, options: CountOptions = CountOptions()): Long =
    countDocuments(toBson(filter), options)

/**
 * Gets the distinct values of the specified field name.
 *
 * @param fieldName   the field name
 * @param filter      the query filter
 * @param <TResult>   the target type of the iterable.
 *
 * @return an iterable of distinct values
 */
inline fun <reified TResult : Any> MongoCollection<*>.distinct(
    fieldName: String,
    filter: String = EMPTY_JSON
): DistinctIterable<TResult> = distinct(fieldName, toBson(filter), TResult::class.java)


/**
 * Gets the distinct values of the specified field.
 *
 * @param field   the field
 * @param filter      the query filter
 * @param <TResult>   the target type of the iterable.
 *
 * @return an iterable of distinct values
 */
inline fun <reified T : Any, reified TResult> MongoCollection<T>.distinct(
    field: KProperty1<T, TResult>,
    filter: Bson = EMPTY_BSON
): DistinctIterable<TResult> = distinct(field.path(), filter, TResult::class.java)

/**
 * Finds all documents in the collection.
 *
 * @param filter the query filter
 * @return the find iterable interface
 */
fun <T> MongoCollection<T>.find(filter: String = EMPTY_JSON): FindIterable<T> = find(toBson(filter))

/**
 * Finds all documents in the collection.
 *
 * @param filters the query filters
 * @return the find iterable interface
 */
fun <T> MongoCollection<T>.find(vararg filters: Bson?): FindIterable<T> = find(and(*filters))

/**
 * Finds the first document that match the filter in the collection.
 *
 * @param filter the query filter
 * @return the first item returned or null
 */
fun <T> MongoCollection<T>.findOne(filter: String = EMPTY_JSON): T? = find(filter).firstOrNull()

/**
 * Finds the first document that match the filter in the collection.
 *
 * @param filter the query filter
 * @return the first item returned or null
 */
fun <T> MongoCollection<T>.findOne(filter: Bson): T? = find(filter).firstOrNull()

/**
 * Finds the first document that match the filters in the collection.
 *
 * @param filters the query filters
 * @return the first item returned or null
 */
fun <T> MongoCollection<T>.findOne(vararg filters: Bson?): T? =
    find(*filters).firstOrNull()

/**
 * Finds the first document that match the filter in the collection.
 *
 * @param filters the query filters
 * @return the first item returned or null
 */
inline fun <reified T : Any> MongoCollection<T>.findOne(filters: () -> Bson): T? = findOne(filters())

/**
 * Finds the document that match the id parameter.
 *
 * @param id       the object id
 * @return the first item returned or null
 */
fun <T> MongoCollection<T>.findOneById(id: Any): T? = findOne(idFilterQuery(id))

/**
 * Aggregates documents according to the specified aggregation pipeline.
 *
 * @param pipeline    the aggregate pipeline
 * @param <TResult>   the target document type of the iterable.
 *
 * @return an iterable containing the result of the aggregation operation
 */
inline fun <reified TResult : Any> MongoCollection<*>.aggregate(vararg pipeline: String): AggregateIterable<TResult> =
    aggregate(toBsonList(pipeline, codecRegistry), TResult::class.java)

/**
 * Aggregates documents according to the specified aggregation pipeline.
 *
 * @param pipeline    the aggregate pipeline
 * @param <TResult>   the target document type of the iterable.
 *
 * @return an iterable containing the result of the aggregation operation
 */
inline fun <reified TResult : Any> MongoCollection<*>.aggregate(vararg pipeline: Bson): AggregateIterable<TResult> =
    aggregate(pipeline.toList(), TResult::class.java)

/**
 * Aggregates documents according to the specified map-reduce function.
 *
 * @param mapFunction    A JavaScript function that associates or "maps" a value with a key and emits the key and value pair.
 * @param reduceFunction A JavaScript function that "reduces" to a single object all the values associated with a particular key.
 * @param <TResult>      the target document type of the iterable.
 *
 *  @return an iterable containing the result of the map-reduce operation
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified TResult : Any> MongoCollection<*>.mapReduce(
    mapFunction: String,
    reduceFunction: String
): MapReduceIterable<TResult> = mapReduceWith(mapFunction, reduceFunction)

/**
 * Aggregates documents according to the specified map-reduce function.
 *
 * @param mapFunction    A JavaScript function that associates or "maps" a value with a key and emits the key and value pair.
 * @param reduceFunction A JavaScript function that "reduces" to a single object all the values associated with a particular key.
 * @param <TResult>      the target document type of the iterable.
 *
 *  @return an iterable containing the result of the map-reduce operation
 */
inline fun <reified TResult : Any> MongoCollection<*>.mapReduceWith(
    mapFunction: String,
    reduceFunction: String
): MapReduceIterable<TResult> = mapReduce(mapFunction, reduceFunction, TResult::class.java)

/**
 * Inserts the provided document. If the document is missing an identifier, the driver should generate one.
 *
 * @param document the document to insert
 * @param options  the options to apply to the operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the insert command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoCommandException      if the write failed due to document validation reasons
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
inline fun <reified T : Any> MongoCollection<T>.insertOne(
    document: String,
    options: InsertOneOptions = InsertOneOptions()
) = withDocumentClass<BsonDocument>().insertOne(toBson(document, T::class), options)

/**
 * Removes at most one document from the collection that matches the given filter.  If no documents match, the collection is not
 * modified.
 *
 * @param filter the query filter to apply the the delete operation
 *
 * @return the result of the remove one operation
 *
 * @throws com.mongodb.MongoWriteException       if the write failed due some other failure specific to the delete command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.deleteOne(filter: String): DeleteResult = deleteOne(toBson(filter))

/**
 * Removes at most one document from the collection that matches the given filter.  If no documents match, the collection is not
 * modified.
 *
 * @param filters the query filters to apply the the delete operation
 *
 * @return the result of the remove one operation
 *
 * @throws com.mongodb.MongoWriteException       if the write failed due some other failure specific to the delete command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.deleteOne(vararg filters: Bson?): DeleteResult = deleteOne(and(*filters))

/**
 * Removes at most one document from the id parameter.  If no documents match, the collection is not
 * modified.
 *
 * @param id   the object id
 *
 * @throws com.mongodb.MongoWriteException       if the write failed due some other failure specific to the delete command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.deleteOneById(id: Any): DeleteResult = deleteOne(idFilterQuery(id))

/**
 * Removes all documents from the collection that match the given query filter.  If no documents match, the collection is not modified.
 *
 * @param filter the query filter to apply the the delete operation
 * @param options  the options to apply to the delete operation
 *
 * @return the result of the remove many operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the delete command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.deleteMany(filter: String, options: DeleteOptions = DeleteOptions()): DeleteResult =
    deleteMany(toBson(filter), options)

/**
 * Removes all documents from the collection that match the given query filter.  If no documents match, the collection is not modified.
 *
 * @param filters the query filters to apply the the delete operation
 * @param options  the options to apply to the delete operation
 *
 * @return the result of the remove many operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the delete command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.deleteMany(vararg filters: Bson?, options: DeleteOptions = DeleteOptions()): DeleteResult =
    deleteMany(and(*filters), options)

/**
 * Save the document.
 * If the document has no id field, or if the document has a null id value, insert the document.
 * Otherwise, call [replaceOneById] with upsert true.
 *
 * @param document the document to save
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T : Any> MongoCollection<T>.save(document: T) {
    val id = KMongoUtil.getIdValue(document)
    if (id != null) {
        replaceOneById(id, document, ReplaceOptions().upsert(true))
    } else {
        insertOne(document)
    }
}

/**
 * Replace a document in the collection according to the specified arguments.
 *
 * @param id          the object id
 * @param replacement the replacement document
 * @param options     the options to apply to the replace operation
 * @return the result of the replace one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T : Any> MongoCollection<T>.replaceOneById(
    id: Any,
    replacement: T,
    options: ReplaceOptions = ReplaceOptions()
): UpdateResult = withDocumentClass<BsonDocument>().replaceOne(idFilterQuery(id), filterIdToBson(replacement), options)

/**
 * Replace a document in the collection according to the specified arguments.
 *
 * @param replacement the document to replace - must have an non null id
 * @param options     the options to apply to the replace operation
 * @return the result of the replace one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
inline fun <reified T : Any> MongoCollection<T>.replaceOne(
    replacement: T,
    options: ReplaceOptions = ReplaceOptions()
): UpdateResult = replaceOneById(extractId(replacement, T::class), replacement, options)

/**
 * Replace a document in the collection according to the specified arguments.
 *
 * @param filter      the query filter to apply to the replace operation
 * @param replacement the replacement document
 * @param options     the options to apply to the replace operation
 * @return the result of the replace one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T : Any> MongoCollection<T>.replaceOne(
    filter: String,
    replacement: T,
    options: ReplaceOptions = ReplaceOptions()
): UpdateResult = withDocumentClass<BsonDocument>()
    .replaceOne(
        toBson(filter),
        filterIdToBson(replacement),
        options
    )

/**
 * Replace a document in the collection according to the specified arguments.
 * Same than [MongoCollection.replaceOne] but ensure that any _id present
 * in [replacement] is removed to avoid MongoWriteException such as:
 * "After applying the update, the (immutable) field '_id' was found to have been altered to _id"
 *
 * Note: Supports retryable writes on MongoDB server versions 3.6 or higher when the retryWrites setting is enabled.
 * @param filter        the query filter to apply the the replace operation
 * @param replacement   the replacement document
 * @param replaceOptions the options to apply to the replace operation
 * @return the result of the replace one operation
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the replace command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 * @since 3.7
 */
fun <T : Any> MongoCollection<T>.replaceOneWithFilter(
    filter: Bson,
    replacement: T,
    replaceOptions: ReplaceOptions = ReplaceOptions()
): UpdateResult = withDocumentClass<BsonDocument>().replaceOne(filter, filterIdToBson(replacement), replaceOptions)

/**
 * Update a single document in the collection according to the specified arguments.
 *
 * @param filter   a document describing the query filter
 * @param update   a document describing the update. The update to apply must include only update operators.
 * @param options  the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.updateOne(
    filter: String,
    update: String,
    options: UpdateOptions = UpdateOptions()
): UpdateResult = updateOne(toBson(filter), toBson(update), options)

/**
 * Update a single document in the collection according to the specified arguments.
 *
 * @param filter   a document describing the query filter
 * @param update   the update object
 * @param options  the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.updateOne(
    filter: String,
    update: Any,
    options: UpdateOptions = UpdateOptions()
): UpdateResult = updateOne(toBson(filter), toBsonModifier(update), options)

/**
 * Update a single document in the collection according to the specified arguments.
 *
 * @param target  the update object - must have an non null id
 * @param options  the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
inline fun <reified T : Any> MongoCollection<T>.updateOne(
    target: T,
    options: UpdateOptions = UpdateOptions()
): UpdateResult = updateOneById(extractId(target, T::class), target, options)

/**
 * Update a single document in the collection according to the specified arguments.
 *
 * @param filter   a document describing the query filter
 * @param update   the update object
 * @param options  the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.updateOne(
    filter: Bson,
    target: Any,
    options: UpdateOptions = UpdateOptions()
): UpdateResult = updateOne(filter, toBsonModifier(target), options)

/**
 * Update a single document in the collection according to the specified arguments.
 *
 * @param filter   a document describing the query filter
 * @param updates   the setTo describing the updates
 * @param options  the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T : Any> MongoCollection<T>.updateOne(
    filter: Bson,
    vararg updates: SetTo<*>,
    updateOptions: UpdateOptions = UpdateOptions()
): UpdateResult = updateOne(filter, set(*updates), updateOptions)

/**
 * Update a single document in the collection according to the specified arguments.
 *
 * @param id        the object id
 * @param update    the update object
 * @param options  the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T> MongoCollection<T>.updateOneById(id: Any, update: Any, options: UpdateOptions = UpdateOptions()): UpdateResult =
    updateOne(idFilterQuery(id), toBsonModifier(update), options)

/**
 * Update all documents in the collection according to the specified arguments.
 *
 * @param filter        a document describing the query filter, which may not be null.
 * @param update        a document describing the update, which may not be null. The update to apply must include only update operators.
 * @param updateOptions the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T : Any> MongoCollection<T>.updateMany(
    filter: String,
    update: String,
    updateOptions: UpdateOptions = UpdateOptions()
): UpdateResult = updateMany(toBson(filter), toBson(update), updateOptions)

/**
 * Update all documents in the collection according to the specified arguments.
 *
 * @param filter        a document describing the query filter, which may not be null.
 * @param updates        a document describing the update, which may not be null. The update to apply must include only update operators.
 * @param updateOptions the options to apply to the update operation
 *
 * @return the result of the update one operation
 *
 * @throws com.mongodb.MongoWriteException        if the write failed due some other failure specific to the update command
 * @throws com.mongodb.MongoWriteConcernException if the write failed due being unable to fulfil the write concern
 * @throws com.mongodb.MongoException             if the write failed due some other failure
 */
fun <T : Any> MongoCollection<T>.updateMany(
    filter: Bson,
    vararg updates: SetTo<*>,
    updateOptions: UpdateOptions = UpdateOptions()
): UpdateResult = updateMany(filter, set(*updates), updateOptions)

/**
 * Atomically find a document and remove it.
 *
 * @param filter  the query filter to find the document with
 * @param options the options to apply to the operation
 *
 * @return the document that was removed.  If no documents matched the query filter, then null will be returned
 */
fun <T> MongoCollection<T>.findOneAndDelete(
    filter: String,
    options: FindOneAndDeleteOptions = FindOneAndDeleteOptions()
): T? = findOneAndDelete(toBson(filter), options)

/**
 * Atomically find a document and replace it.
 *
 * @param filter      the query filter to apply the the replace operation
 * @param replacement the replacement document
 * @param options     the options to apply to the operation
 *
 * @return the document that was replaced.  Depending on the value of the `returnOriginal` property, this will either be the
 * document as it was before the update or as it is after the update.  If no documents matched the query filter, then null will be
 * returned
 */
fun <T> MongoCollection<T>.findOneAndReplace(
    filter: String,
    replacement: T,
    options: FindOneAndReplaceOptions = FindOneAndReplaceOptions()
): T? = findOneAndReplace(toBson(filter), replacement, options)

/**
 * Atomically find a document and update it.
 *
 * @param filter  a document describing the query filter, which may not be null.
 * @param update  a document describing the update, which may not be null. The update to apply must include only update operators.
 * @param options the options to apply to the operation
 *
 * @return the document that was updated.  Depending on the value of the `returnOriginal` property, this will either be the
 * document as it was before the update or as it is after the update.  If no documents matched the query filter, then null will be
 * returned
 */
fun <T> MongoCollection<T>.findOneAndUpdate(
    filter: String,
    update: String,
    options: FindOneAndUpdateOptions = FindOneAndUpdateOptions()
): T? = findOneAndUpdate(toBson(filter), toBson(update), options)

/**
 * Create an index with the given keys and options.
 *
 * @param keys an object describing the index key(s), which may not be null.
 * @param indexOptions the options for the index
 * @return the index name
 */
fun <T> MongoCollection<T>.createIndex(keys: String, indexOptions: IndexOptions = IndexOptions()): String =
    createIndex(toBson(keys), indexOptions)

/**
 * Create an index with the given keys and options.
 * If the creation of the index is not doable because an index with the same keys but with different [IndexOptions]
 * already exists, then drop the existing index and create a new one.
 *
 * @param keys an object describing the index key(s), which may not be null.
 * @param indexOptions the options for the index
 * @return the index name
 */
fun <T> MongoCollection<T>.ensureIndex(keys: Bson, indexOptions: IndexOptions = IndexOptions()): String {
    return try {
        createIndex(keys, indexOptions)
    } catch (e: MongoCommandException) {
        //there is an exception if the parameters of an existing index are changed.
        //then drop the index and create a new one
        dropIndex(keys)
        createIndex(keys, indexOptions)
    }
}

/**
 * Create an ascending index with the given keys and options.
 * If the creation of the index is not doable because an index with the same keys but with different [IndexOptions]
 * already exists, then drop the existing index and create a new one.
 *
 * @param keys the properties, which must contain at least one
 * @param indexOptions the options for the index
 * @return the index name
 */
fun <T> MongoCollection<T>.ensureIndex(
    vararg properties: KProperty<*>,
    indexOptions: IndexOptions = IndexOptions()
): String = ensureIndex(ascending(*properties), indexOptions)

/**
 * Create an [IndexOptions.unique] index with the given keys and options.
 * If the creation of the index is not doable because an index with the same keys but with different [IndexOptions]
 * already exists, then drop the existing index and create a new one.
 *
 * @param keys the properties, which must contain at least one
 * @param indexOptions the options for the index
 * @return the index name
 */
fun <T> MongoCollection<T>.ensureUniqueIndex(
    vararg properties: KProperty<*>,
    indexOptions: IndexOptions = IndexOptions()
): String = ensureIndex(properties = *properties, indexOptions = indexOptions.unique(true))

/**
 * Create an index with the given keys and options.
 * If the creation of the index is not doable because an index with the same keys but with different [IndexOptions]
 * already exists, then drop the existing index and create a new one.
 *
 * @param keys an object describing the index key(s), which may not be null.
 * @param indexOptions the options for the index
 * @return the index name
 */
fun <T> MongoCollection<T>.ensureIndex(keys: String, indexOptions: IndexOptions = IndexOptions()): String {
    return try {
        createIndex(keys, indexOptions)
    } catch (e: MongoCommandException) {
        //there is an exception if the parameters of an existing index are changed.
        //then drop the index and create a new one
        dropIndexOfKeys(keys)
        createIndex(keys, indexOptions)
    }
}

/**
 * Get all the indexes in this collection.
 *
 * @param <TResult>   the target document type of the iterable.
 * @return the list indexes iterable interface
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified TResult : Any> MongoCollection<*>.listIndexes(): ListIndexesIterable<TResult> = listTypedIndexes()

/**
 * Get all the indexes in this collection.
 *
 * @param <TResult>   the target document type of the iterable.
 * @return the list indexes iterable interface
 */
inline fun <reified TResult : Any> MongoCollection<*>.listTypedIndexes(): ListIndexesIterable<TResult> =
    listIndexes(TResult::class.java)


/**
 * Drops the index given the keys used to create it.
 *
 * @param keys the keys of the index to remove
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun <T> MongoCollection<T>.dropIndex(keys: String) = dropIndexOfKeys(keys)

/**
 * Drops the index given the keys used to create it.
 *
 * @param json the keys of the index to remove
 */
fun <T> MongoCollection<T>.dropIndexOfKeys(json: String) = dropIndex(toBson(json))

/**
 * Executes a mix of inserts, updates, replaces, and deletes.
 *
 * @param requests the writes to execute
 * @param options  the options to apply to the bulk write operation
 *
 * @return the result of the bulk write
 */
inline fun <reified T : Any> MongoCollection<T>.bulkWrite(
    vararg requests: String,
    options: BulkWriteOptions = BulkWriteOptions()
): BulkWriteResult =
    withDocumentClass<BsonDocument>().bulkWrite(toWriteModel(requests, codecRegistry, T::class), options)

/**
 * Executes a mix of inserts, updates, replaces, and deletes.
 *
 * @param requests the writes to execute
 * @param options  the options to apply to the bulk write operation
 *
 * @return the result of the bulk write
 */
inline fun <reified T : Any> MongoCollection<T>.bulkWrite(
    vararg requests: WriteModel<T>,
    options: BulkWriteOptions = BulkWriteOptions()
): BulkWriteResult = bulkWrite(requests.toList(), options)

//*******
//IndexModel extension methods
//*******

/**
 * Construct an instance with the given keys and options.
 *
 * @param keys the index keys
 * @param options the index options
 */
fun IndexModel.IndexModel(keys: String, options: IndexOptions = IndexOptions()): IndexModel =
    IndexModel(toBson(keys), options)

//*******
//DistinctIterable extension methods
//*******

/**
 * Sets the query filter to apply to the query.
 *
 * @param filter the filter, which may be null
 * @return this
 */
fun <T> DistinctIterable<T>.filter(filter: String): DistinctIterable<T> = filter(toBson(filter))

//*******
//FindIterable extension methods
//*******

/**
 * Sets the query filter to apply to the query.
 *
 * @param filter the filter, which may be null
 * @return this
 */
fun <T> FindIterable<T>.filter(filter: String): FindIterable<T> = filter(toBson(filter))

/**
 * Sets the query modifiers to apply to this operation.
 *
 * @param modifiers the query modifiers to apply
 * @return this
 */
fun <T> FindIterable<T>.modifiers(modifiers: String): FindIterable<T> = modifiers(toBson(modifiers))

/**
 * Sets a document describing the fields to return for all matching documents.
 *
 * @param projection the project document
 * @return this
 */
fun <T> FindIterable<T>.projection(projection: String): FindIterable<T> = projection(toBson(projection))

/**
 * Sets a document describing the fields to return for all matching documents.
 *
 * @param projections the properties of the returned fields
 * @return this
 */
fun <T> FindIterable<T>.projection(vararg projections: KProperty<*>): FindIterable<T> =
    projection(include(*projections))


/**
 * Sets the sort criteria to apply to the query.
 *
 * @param sort the sort criteria
 * @return this
 */
fun <T> FindIterable<T>.sort(sort: String): FindIterable<T> = sort(toBson(sort))

/**
 * Sets the sort criteria with specified ascending properties to apply to the query.
 *
 * @param properties the properties
 * @return this
 */
fun <T> FindIterable<T>.ascendingSort(vararg properties: KProperty<*>): FindIterable<T> = sort(ascending(*properties))

/**
 * Sets the sort criteria with specified descending properties to apply to the query.
 *
 * @param properties the properties
 * @return this
 */
fun <T> FindIterable<T>.descendingSort(vararg properties: KProperty<*>): FindIterable<T> = sort(descending(*properties))


//*******
//MapReduceIterable extension methods
//*******

/**
 * Sets the global variables that are accessible in the map, reduce and finalize functions.
 *
 * @param scope the global variables that are accessible in the map, reduce and finalize functions.
 * @return this
 */
fun <T> MapReduceIterable<T>.scope(scope: String): MapReduceIterable<T> = scope(toBson(scope))

/**
 * Sets the sort criteria to apply to the query.
 *
 * @param sort the sort criteria, which may be null
 * @return this
 */
fun <T> MapReduceIterable<T>.sort(sort: String): MapReduceIterable<T> = sort(toBson(sort))

/**
 * Sets the query filter to apply to the query.
 *
 * @param filter the filter to apply to the query
 * @return this
 */
fun <T> MapReduceIterable<T>.filter(filter: String): MapReduceIterable<T> = filter(toBson(filter))

//*******
//MongoIterable extension methods
//*******

/**
 * Iterates over all the documents, adding each to the given target.
 *
 * @param target   the collection to insert into
 * @param callback a callback that will be passed the target containing all documents
 */
@Deprecated("use classic toList extension")
fun <T> MongoIterable<T>.toList(): List<T> = into(mutableListOf<T>())

//*******
//ChangeStreamIterable extension methods
//*******

/**
 * Listens change stream in an other thread.
 *
 * @param executor the executor service for the thread instantiation - default is [Executors.newSingleThreadExecutor]
 * @param delay the delay the executor is waiting before submitting the task
 * @param unit the unit of the [delay]
 * @param listener to listen changes
 */
fun <T> ChangeStreamIterable<T>.listen(
    executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    delay: Long = 500,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    listener: (ChangeStreamDocument<T>) -> Unit
) {
    //need to listen the iterator from the original thread
    val cursor = iterator()
    executor.schedule(
        {
            cursor.use { cursor ->
                while (cursor.hasNext()) {
                    listener(cursor.next())
                }
            }
        },
        delay,
        unit
    )
}

