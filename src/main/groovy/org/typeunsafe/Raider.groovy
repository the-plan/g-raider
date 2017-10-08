package org.typeunsafe


import groovy.json.JsonOutput
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.servicediscovery.Record
import io.vertx.servicediscovery.ServiceDiscovery
import io.vertx.servicediscovery.ServiceDiscoveryOptions
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint
import io.vertx.servicediscovery.types.HttpEndpoint

import io.vertx.circuitbreaker.CircuitBreakerOptions

import me.atrox.haikunator.HaikunatorBuilder

//import io.vertx.groovy.ext.web.Router
//import io.vertx.groovy.ext.web.handler.StaticHandler


class Raider extends AbstractVerticle {

    ServiceDiscovery discovery = null
    Record record = null
    BaseStar baseStar = null
    HealthCheckHandler healthCheck = null
    CircuitBreaker breaker = null

    Double x = 0.0
    Double y = 0.0

    Double xVelocity = 0.0
    Double yVelocity = 0.0

    @Override
    void stop(Future<Void> stopFuture) throws Exception {
        super.stop()
        println("Unregistration process is started (${record?.registration})...")

        discovery?.unpublish(record?.registration, { ar ->
            if(ar.failed()) {
                println("😡 Unable to unpublish the microservice: ${ar.cause().message}")
                stopFuture.fail(ar.cause())
            } else {
                println("👋 bye bye ${record?.registration}")
                stopFuture.complete()
            }
        })
    }

    def watchingMyBaseStar(BaseStar baseStar) {
        println("@@@ subscribeToBaseStar @@@")

        vertx.setPeriodic(1000, { timerId ->
            // and the breaker ??? TODO: use the breaker before ?
            baseStar.client.get("/health").send { asyncGetRes ->

                if(asyncGetRes.failed()) {
                    record?.metadata?.getJsonObject("coordinates")
                            ?.put("xVelocity",0)
                            ?.put("yVelocity",0)
                    // btw, you never stop in space 👾
                    discovery?.update(record, {asyncUpdateResult ->
                        println("${record?.name} 😡😡😡 I'm alone ???")
                        // time to search a new basestar
                        searchAndSelectOneBaseStar()
                        vertx.setTimer(3000, { id ->
                            println("canceling timer")
                            vertx.cancelTimer(timerId)
                            //searchAndSelectOneBaseStar()
                        })
                    })

                    // if we have no more basestar ??? => had to be managed in searchAndSelectOneBaseStar()
                } else {

                    // 😃 === all is fine ===
                    //println(asyncGetRes.result().bodyAsJsonObject())
                }

            }
        })

    }




    void subscribeToBaseStar(Record selectedRecord) {
        println("### subscribeToBaseStar ###")

        def serviceReference = discovery?.getReference(selectedRecord)
        //def webClient = serviceReference?.getAs(WebClient::class.java)
        def webClient = serviceReference?.getAs(WebClient.class)

        // 👋 === CIRCUIT BREAKER === try to register to the basestar

        breaker.execute({ future ->
            webClient.post("/api/raiders").sendJson(JsonObject.mapFrom(["registration" : record?.registration]), { baseStarResponse ->
                if(baseStarResponse.failed()) {
                    this.baseStar = null // 😧 remove the basestar
                    future.fail("😡 ouch something bad happened")
                } else {
                    println("👏 you found a basestar ${baseStarResponse?.result()?.bodyAsJsonObject()?.encodePrettily()}")
                    def selectedBaseStar = new BaseStar(selectedRecord, webClient)
                    this.baseStar = selectedBaseStar
                    // 👀--- time to check the health of my basestar

                    watchingMyBaseStar(selectedBaseStar)

                    future.complete("😃 yesss!")
                }
            })
        })?.setHandler({ breakerResult ->
            // TODO: eg, kill the raider
            // Do something with the result when future completed or failed
        })

    }


    void searchAndSelectOneBaseStar() {
        println("+++ searchAndSelectOneBaseStar +++")

        /* 🤖 === search for a baseStar in the discovery backend === */

        discovery?.getRecords(
            {r -> r.metadata.getString("kind") == "basestar" && r.status == io.vertx.servicediscovery.Status.UP },
            { asyncResult ->
                if(asyncResult.failed()) {

                } else {
                    def baseStarsRecords = asyncResult.result()
                    // === choose randomly a basestar ===
                    if(baseStarsRecords.size > 0) {
                        def selectedRecord = baseStarsRecords.get(new Random().nextInt(baseStarsRecords.size)) // ? -1

                        subscribeToBaseStar(selectedRecord)
                    } else {
                        searchAndSelectOneBaseStar()
                    }
                }

            }
        ) // end of the discovery
    }


    @Override
    void start() {

        def random =  { min, max ->
            return (Math.random() * (max+1.0-min))+min
        }

        /* 🔦 === Discovery part === */

        // Redis Backend settings

        def redisPort= System.getenv("REDIS_PORT")?.toInt() ?: 6379
        def redisHost = System.getenv("REDIS_HOST") ?: "127.0.0.1"
        def redisAuth = System.getenv("REDIS_PASSWORD") ?: null
        def redisRecordsKey = System.getenv("REDIS_RECORDS_KEY") ?: "vert.x.ms" // the redis hash

        ServiceDiscoveryOptions serviceDiscoveryOptions = new ServiceDiscoveryOptions()

        discovery = ServiceDiscovery.create(vertx, serviceDiscoveryOptions.setBackendConfiguration(JsonObject.mapFrom([
                "host" : redisHost,
                "port" : redisPort,
                "auth" : redisAuth,
                "key" : redisRecordsKey
        ])))


        // microservice informations
        def haikunator = new HaikunatorBuilder().setTokenLength(3).build()
        def niceName = haikunator.haikunate()

        def serviceName = "${System.getenv("SERVICE_NAME") ?: "the-plan"}-$niceName"
        def serviceHost = System.getenv("SERVICE_HOST") ?: "localhost" // domain name
        def servicePort = Integer.parseInt(Optional.ofNullable(System.getenv("SERVICE_PORT")).orElse("80"))
        def serviceRoot = System.getenv("SERVICE_ROOT") ?: "/api"

        // create the microservice record
        record = HttpEndpoint.createRecord(
            serviceName,
            serviceHost,
            servicePort,
            serviceRoot
        )

        record?.metadata = JsonObject.mapFrom([
                "kind" : "raider",
                "message" : "🚀 ready to fight",
                "basestar" : null,
                "coordinates": ["x" : random(0.0, 400.0), "y" : random(0.0, 400.0)],
                "app_id" : (System.getenv("APP_ID") ?: "🤖"),
                "instance_id" : (System.getenv("INSTANCE_ID") ?: "🤖"),
                "instance_type" : (System.getenv("INSTANCE_TYPE") ?: "production"),
                "instance_number" : (Integer.parseInt(System.getenv("INSTANCE_NUMBER") ?: "0"))
        ])

        /* 🤖 === health check === */
        healthCheck = HealthCheckHandler.create(vertx)
        healthCheck?.register("iamok",{ future ->
            discovery?.getRecord({ r -> r.registration == record?.registration}, {
                asyncRes ->
                    if(asyncRes.failed()) {
                        future.fail(asyncRes.cause())
                    } else {
                        future.complete(Status.OK())
                    }
            })
        })

        println("🎃  " + record?.toJson()?.encodePrettily())

        /* 🚦 === Define a circuit breaker === */

        CircuitBreakerOptions circuitBreakerOptions = new CircuitBreakerOptions()
        circuitBreakerOptions.maxFailures = 5
        circuitBreakerOptions.timeout =20000
        circuitBreakerOptions.fallbackOnFailure=true
        circuitBreakerOptions.resetTimeout=100000


        breaker = CircuitBreaker.create("bsg-circuit-breaker", vertx, circuitBreakerOptions)

        /* === Define routes === */
        def router = Router.router(vertx)
        router.route().handler(BodyHandler.create())


        // call by a basestar
        router.post("/api/coordinates").handler { context ->

            // check data -> if null, don't move
            def computedX =  context.bodyAsJson.getDouble("x") ?: x
            def computedY =  context.bodyAsJson.getDouble("y") ?: y

            def computedXVelocity =  context.bodyAsJson.getDouble("xVelocity") ?: xVelocity
            def computedYVelocity =  context.bodyAsJson.getDouble("yVelocity") ?: yVelocity

            println("🚀 (${record?.name}) moves: $computedX - $computedY thx to ${baseStar?.record?.name}")

            /* 💾 === updating record of the service === */

            record.metadata.getJsonObject("coordinates")
                    .put("x", computedX)
                    .put("y",computedY)
                    .put("xVelocity",computedXVelocity)
                    .put("yVelocity",computedYVelocity)

            //record?.metadata?.put("basestar", baseStar?.record?.name)

            record.metadata.put("basestar", [
                "name:" : baseStar.record.name,
                "color" : baseStar.record.metadata.getString("color")
            ])

            // "color" : baseStar.record.metadata.get("color")


            discovery.update(record, {asyncUpdateResult ->
                // foo
            })

            context
              .response()
              .putHeader("content-type", "application/json;charset=UTF-8")
              .end(JsonOutput.toJson([
                  "message" : "👍", "x" : computedX, "y" : computedY
                ]))

        }



        // use me with other microservices
        ServiceDiscoveryRestEndpoint.create(router, discovery)

        // link/bind healthCheck to a route
        router.get("/health").handler(healthCheck)

        router.route("/*").handler(StaticHandler.create())

        /* === Start the server === */

        def httpPort = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("9999"))

        def server = vertx.createHttpServer()


        server.requestHandler(router.&accept).listen(httpPort)

        println("😃 🌍 Raider started on $httpPort")

        /* 👋 === publish the microservice record to the discovery backend === */
        discovery.publish(record, { asyncRes ->
            if(asyncRes.failed()) {
                println("😡 Not able to publish the microservice: ${asyncRes.cause().message}")
            } else {
                println("😃 Microservice is published! ${asyncRes.result().registration}")
                searchAndSelectOneBaseStar()
            }
        }) // publish
    }
}
