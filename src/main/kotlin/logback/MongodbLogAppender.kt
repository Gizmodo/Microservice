package logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.UnsynchronizedAppenderBase
import com.mongodb.BasicDBObject
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.client.result.InsertOneResult
import com.mongodb.connection.ClusterSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.reactivestreams.client.MongoDatabase
import com.skydoves.whatif.whatIf
import org.bson.Document
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.net.InetAddress
import java.util.*

class MongodbLogAppender<E : ILoggingEvent> : UnsynchronizedAppenderBase<E>() {
    private var credentials: MongoCredential? = null
    private var collection: MongoCollection<Document>? = null
    private var client: MongoClient? = null
    var dbname: String = "microservice"
    var dbCollection: String = "logs"
    var username: String? = null
    var password: String? = null
    var port: String = "27017"
    var host: String = "127.0.0.1"
    override fun start() {
        try {
            connect()
            super.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun append(eventObject: E) {
        val doc = newDocument(eventObject)
        val publisher: Publisher<InsertOneResult> = collection!!.insertOne(doc)
        publisher.subscribe(object : Subscriber<InsertOneResult?> {
            override fun onSubscribe(s: Subscription) {
                s.request(1) // <--- Data requested and the insertion will now occur
            }

            override fun onError(t: Throwable?) {
                println("onError: ${t?.message}")
            }

            override fun onComplete() {
            }

            override fun onNext(t: InsertOneResult?) {
            }
        })
    }

    private fun connect() {
        username?.let { user ->
            password?.let { pass ->
                credentials = MongoCredential.createCredential(
                    user,
                    dbname, pass.toCharArray()
                )

            }
        }

        val set: MongoClientSettings = MongoClientSettings
            .builder()
            .applyToClusterSettings { builder: ClusterSettings.Builder ->
                builder.hosts(
                    listOf(
                        when (port.isBlank()) {
                            true -> ServerAddress(host)
                            else -> ServerAddress(host, port.toInt())
                        }
                    )
                )
            }
            .whatIf(credentials != null) {
                credential(credentials)
            }
            .build()

        client = MongoClients.create(set)
        val database: MongoDatabase = client!!.getDatabase(dbname)
        collection = database.getCollection(dbCollection)
    }

    private fun newDocument(event: E): Document {
        val doc = Document("applicationName", InetAddress.getLocalHost().hostAddress ?: "")
            .append("datetime", Date(event.timeStamp))
            .append("logger", event.loggerName)
            .append("levelInt", event.level.toInt())
            .append("levelStr", event.level.levelStr)
            .append("threadName", event.threadName)
            .append("message", event.message)

        if (event.markerList != null) {
            doc.append("marker", event.markerList[0].name)
        }

        event.throwableProxy?.let { tp ->
            val str = ThrowableProxyUtil.asString(tp)
            val stacktrace = str.replace("\t", "")
                .split(CoreConstants.LINE_SEPARATOR)
                .toMutableList()

            if (stacktrace.size > 0)
                doc.append("exception", stacktrace[0])

            if (stacktrace.size > 1) {
                stacktrace.removeAt(1)
                doc.append("stackTrace", stacktrace)
            }
        }
        if (event.hasCallerData()) {
            val st = event.callerData[0]
            val callerData = String.format("%s.%s:%d", st.className, st.methodName, st.lineNumber)
            doc.append("caller", callerData)
        }
        val mdc: Map<String?, String?> = event.mdcPropertyMap
        if (mdc.isNotEmpty()) {
            doc.append("mdc", BasicDBObject(mdc))
        }
        return doc
    }
}