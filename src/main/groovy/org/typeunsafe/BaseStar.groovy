package org.typeunsafe

import io.vertx.ext.web.client.WebClient
import io.vertx.servicediscovery.Record

class BaseStar {
    Record record
    WebClient client
    BaseStar(Record record, WebClient client) {
        this.record = record
        this.client = client
    }
}
