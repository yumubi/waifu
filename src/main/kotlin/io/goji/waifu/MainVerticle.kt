
package io.goji.waifu

import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.core.net.ProxyType
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.net.proxyOptionsOf
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory


class MainVerticle {
    private val API_URL = "https://api.waifu.pics"
    private val PORT = System.getenv("PORT")?.toIntOrNull() ?: 8888

    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)

    private val vertx = Vertx.vertx()
    private val server = vertx.createHttpServer()
    private val router = Router.router(vertx)
    private val client = WebClient.create(vertx,
        WebClientOptions()
            .apply {
                proxyOptions = proxyOptionsOf(host="127.0.0.1", port=7890, type = ProxyType.HTTP)
                setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                setFollowRedirects(true)
                isDecompressionSupported = true
                connectTimeout = 5000
                idleTimeout = 10
            }
    )

    fun start() = runBlocking {


        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedMethod(io.vertx.core.http.HttpMethod.GET)
            .allowedMethod(io.vertx.core.http.HttpMethod.POST)
            .allowedHeader("Access-Control-Allow-Origin")
            .allowedHeader("Content-Type"))

        router.route().handler(BodyHandler.create())

        setupRoutes(router, vertx)

        server.requestHandler(router)
            .listen(PORT) { h ->
                if (h.failed()) {
                    logger.error("Failed to start server", h.cause())
                }
                else {

                    logger.info("server: ${h.result()}\nServer is listening on port $PORT")
                }


            }
    }

    private fun setupRoutes(router: Router, vertx: Vertx) {
        // Root endpoint
        router.get("/").handler { ctx ->
            ctx.json(
                mapOf(
                    "message" to "Welcome to the waifu.pics API proxy"
                )
            )
        }

        // Type and endpoint route
        router.get("/:type/:endpoint").handler { ctx ->
            val typeParam = ctx.pathParam("type").lowercase()
            val endpointParam = ctx.pathParam("endpoint").lowercase()

            ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "max-age=0, no-cache, no-store, must-revalidate")

            when {
                // If endpoint is in the corresponding list
                checkValidEndpoint(typeParam, endpointParam) -> {
                    fetchImage(vertx, client, typeParam, endpointParam, ctx)
                }
                // If user requested /type/random
                endpointParam == "random" -> {
                    handleRandomRoute(typeParam, ctx, client)
                }

                else -> {
                    ctx.response().setStatusCode(400).end("""{"message":"Bad endpoint"}""")
                }
            }
        }

        // Random endpoint route
        router.get("/:type").handler { ctx ->
            val typeParam = ctx.pathParam("type").lowercase()
            ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "max-age=0, no-cache, no-store, must-revalidate")

            val epsStr = ctx.request().getParam("eps")
            if (epsStr != null) {
                // Split the provided endpoint list by comma
                val epsList = epsStr.split(",")
                // Pick a random endpoint
                val randomEndpoint = epsList.randomOrNull()

                if (randomEndpoint != null && checkValidEndpoint(typeParam, randomEndpoint)) {
                    fetchImage(vertx, client, typeParam, randomEndpoint, ctx)
                } else {
                    ctx.response().setStatusCode(400).end("""{"message":"Bad endpoint"}""")
                }
            } else {
                ctx.response().setStatusCode(400).end("""{"message":"Missing eps query parameter"}""")
            }
        }


    }


    private fun checkValidEndpoint(type: String, endpoint: String): Boolean {
        return when (type) {
            "sfw" -> Endpoints.endpoints["sfw"]!!.contains(endpoint)
            "nsfw" -> Endpoints.endpoints["nsfw"]!!.contains(endpoint)
            else -> false
        }
    }


    private fun handleRandomRoute(type: String, ctx: RoutingContext, client: WebClient) {
        // If `ignore` is specified, filter out the endpoints
        val ignoreParam = ctx.request().getParam("ignore")
        val endpointsList = when (type) {
            "sfw" -> Endpoints.endpoints["sfw"]!!
            "nsfw" -> Endpoints.endpoints["nsfw"]!!
            else -> {
                ctx.response().setStatusCode(400).end("""{"message":"Bad type"}""")
                return
            }
        }

        val endpoint = if (!ignoreParam.isNullOrBlank()) {
            val ignoreList = ignoreParam.split(",")
            val filteredEps = endpointsList.filter { it !in ignoreList }
            if (filteredEps.isEmpty()) {
                ctx.response().setStatusCode(400).end("""{"message":"All endpoints were ignored"}""")
                return
            }
            filteredEps.random()
        } else {
            endpointsList.random()
        }

        fetchImage(ctx.vertx(), client, type, endpoint, ctx)
    }


    private fun fetchImage(vertx: Vertx, client: WebClient, type: String, endpoint: String, ctx: RoutingContext) {
        val apiUrl = "https://api.waifu.pics/$type/$endpoint"

        // Use coroutines to handle async calls more cleanly
        GlobalScope.launch(vertx.dispatcher()) {
            try {
                // Perform a GET request and parse JSON
                val response = awaitResult<io.vertx.ext.web.client.HttpResponse<io.vertx.core.json.JsonObject>> {
                    client.getAbs(apiUrl).`as`(BodyCodec.jsonObject()).send(it)
                }

                if (response.statusCode() == 200) {
                    val imageUrl = response.body().getString("url")
                    if (imageUrl != null) {
                        streamImage(client, imageUrl, ctx)
                    } else {
                        ctx.response().setStatusCode(500).end("""{"message":"URL not found in response"}""")
                    }
                } else {
                    ctx.response().setStatusCode(response.statusCode()).end("""{"message":"External API Error"}""")
                }
            } catch (e: Exception) {
                ctx.response().setStatusCode(500).end("""{"message":"${e.message}"}""")
            }
        }
    }

    private fun streamImage(client: WebClient, imageUrl: String, ctx: RoutingContext) {
        client.getAbs(imageUrl).send { ar ->
            if (ar.succeeded()) {
                val imageResponse = ar.result()
                // Set response headers
                ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, imageResponse.getHeader(HttpHeaders.CONTENT_TYPE))
                ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "max-age=0, no-cache, no-store, must-revalidate")
                ctx.response().end(imageResponse.body())
            } else {
                ctx.response().setStatusCode(500).end("""{"message":"Failed to fetch image"}""")
            }
        }
    }
}

