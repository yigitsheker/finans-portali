package com.finansportali.logconsumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class LogConsumerService {
    
    private final RestHighLevelClient openSearchClient;
    private final ObjectMapper objectMapper;
    private final String indexPrefix;
    
    public LogConsumerService(
            RestHighLevelClient openSearchClient,
            ObjectMapper objectMapper,
            @Value("${opensearch.index-prefix}") String indexPrefix) {
        this.openSearchClient = openSearchClient;
        this.objectMapper = objectMapper;
        this.indexPrefix = indexPrefix;
    }
    
    @KafkaListener(topics = "finans-logs", groupId = "log-consumer-group")
    public void consumeLog(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            String logMessage = record.value();
            log.debug("Received log message: {}", logMessage);
            
            // Parse JSON log
            JsonNode logJson = objectMapper.readTree(logMessage);
            
            // Create index name with date (e.g., finans-logs-2026-05-08)
            String indexName = indexPrefix + "-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            
            // Index to OpenSearch
            IndexRequest indexRequest = new IndexRequest(indexName)
                    .source(logMessage, XContentType.JSON);
            
            IndexResponse response = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);
            
            log.debug("Indexed log to OpenSearch: index={}, id={}, result={}", 
                    indexName, response.getId(), response.getResult());
            
            // Manually commit offset after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing log message: {}", e.getMessage(), e);
            // Don't acknowledge - message will be reprocessed
        }
    }
}
