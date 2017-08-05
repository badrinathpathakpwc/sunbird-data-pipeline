package org.ekstep.ep.samza.service;

import org.ekstep.ep.samza.domain.Event;
import org.ekstep.ep.samza.esclient.ElasticSearchService;
import org.ekstep.ep.samza.logger.Logger;
import org.ekstep.ep.samza.esclient.ClientResponse;
import org.ekstep.ep.samza.task.EsIndexerPrimarySink;
import org.ekstep.ep.samza.task.EsIndexerPrimarySource;

import java.io.IOException;

public class EsIndexerPrimaryService {
    static Logger LOGGER = new Logger(EsIndexerPrimaryService.class);
    private final ElasticSearchService elasticSearchService;

    public EsIndexerPrimaryService(ElasticSearchService elasticSearchService) {
        this.elasticSearchService = elasticSearchService;
    }

    public void process(EsIndexerPrimarySource source, EsIndexerPrimarySink sink) {

        Event event = source.getEvent();

        try {

            if(!event.can_be_indexed()){
                LOGGER.info("INDEX DETAILS ARE MISSING! SKIPPING", event.id());
                event.markSkipped();
                writeToFailedIndex(event);
                sink.toFailedTopic(event);
                return;
            }

            ClientResponse response = elasticSearchService.index(event.indexName(), event.indexType(), event.getJson(), event.id());

            if(success(response)) {
                LOGGER.info("ES INDEXER SUCCESS", event.id());
            } else {
                LOGGER.error("ES INDEXER FAILED", response.toString());
                event.markFailed(response.getStatus(),response.getMessage());
                writeToFailedIndex(event);
                sink.toFailedTopic(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("ES INDEXER EXCEPTION", e.getMessage());
            event.markFailed("Error", e.getMessage());
            sink.toFailedTopic(event);
        }
    }

    private void writeToFailedIndex(Event event) {
        LOGGER.info("WRITING TO FAILED INDEX", event.id());
        try {
            ClientResponse response = elasticSearchService.index(event.failedIndexName(), event.failedIndexType(), event.getJson(), event.id());
            LOGGER.info("ES INDEXER RESPONSE", response.toString());
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("ES INDEXER EXCEPTION", e.getMessage());
            event.markFailed("Error", e.getMessage());
        }
    }

    private boolean success(ClientResponse response) {
        return (response.getStatus().equals("200") || response.getStatus().equals("201"));
    }
}