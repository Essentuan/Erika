package net.essentuan.erika.fetch.wynncraft

import net.essentuan.erika.fetch.WynnModel
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.JsonRequest
import net.essentuan.esl.fetch.RateLimit
import net.essentuan.esl.fetch.Request
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.time.extensions.timeUntil
import net.essentuan.esl.time.extensions.toDate
import java.net.http.HttpResponse

const val TTL_HEADER = "Cache-Control"
const val DATE_HEADER = "Date"
const val EXPIRES_HEADER = "Expires"
const val LIMIT_HEADER = "Ratelimit-Limit"
const val REMAINING_HEADER = "Ratelimit-Remaining"
const val RESET_HEADER = "Ratelimit-Reset"

const val VERSION_HEADER = "Version"

abstract class WynncraftReq<T : WynnModel>(
    vararg args: Any?,
    rateLimit: RateLimit = RateLimiter
) : JsonRequest<T>(*args, rateLimit = rateLimit) {
    override fun handle(response: HttpResponse<Json>): T? {
        return if (validate(response)) {
            val headers = response.headers()

            this(json {
                "response" to response.body()
                "metadata" {
                    headers.allValues(EXPIRES_HEADER)
                        .firstOrNull()
                        ?.toDate()
                        ?.also {
                            "expires" to it
                        }

                    headers.allValues(TTL_HEADER)
                        .firstOrNull()
                        ?.split('=', limit = 2)
                        ?.get(1)
                        ?.also {
                            "ttl" to it.toDouble()
                        }

                    headers.allValues(LIMIT_HEADER)
                        .firstOrNull()
                        ?.toInt()
                        ?.also { "limit" to it }

                    headers.allValues(REMAINING_HEADER)
                        .firstOrNull()
                        ?.toInt()
                        ?.also { "remaining" to it }

                    headers.allValues(RESET_HEADER)
                        .firstOrNull()
                        ?.toInt()
                        ?.also { "reset" to it }

                    headers.allValues(VERSION_HEADER)
                        .firstOrNull()
                        ?.also { "version" to it }
                }
            })
        } else
            null
    }

    override fun after(response: HttpResponse<Json>) {
        response.headers()
            .allValues(EXPIRES_HEADER)
            .firstOrNull()
            ?.toDate()
            ?.also {
                cache = it.timeUntil()
            }
    }
}


internal object RateLimiter : RateLimit {
    var limit: Int = 180
    var remaining: Int = 180
    var reset: Int = 60

    @Synchronized
    @Every(seconds = 1.0)
    fun tick() {
        if (reset > 0)
            reset--
        else {
            reset = 60
            remaining = limit
        }
    }

    @Synchronized
    override fun ready(request: Request<*, *>): Boolean {
        return if (request.priority == Rating.CRITICAL) {
            remaining > 0
        } else {
            remaining - 25 > 0
        }
    }

    @Synchronized
    override fun request(request: Request<*, *>) { remaining-- }

    @Synchronized
    override fun response(response: HttpResponse<*>) {
        val headers = response.headers()

        headers.allValues(LIMIT_HEADER)
            .firstOrNull()
            ?.toInt()
            ?.also { limit = it }

        headers.allValues(REMAINING_HEADER)
            .firstOrNull()
            ?.toInt()
            ?.also { remaining = it }

        headers.allValues(RESET_HEADER)
            .firstOrNull()
            ?.toInt()
            ?.also { reset = it }
    }
}