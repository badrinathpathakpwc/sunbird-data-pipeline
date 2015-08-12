package org.ekstep.ep.samza.task;

import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.*;
import org.ekstep.ep.samza.model.Child;
import org.ekstep.ep.samza.model.Database;
import org.ekstep.ep.samza.model.Event;

import java.util.Map;

public class DeNormalizationTask implements StreamTask, InitableTask{
    private String successTopic;
    private String failedTopic;
    private KeyValueStore<String, Child> childData;
    private String dbHost;
    private String dbPort;
    private String dbUserName;
    private String dbPassword;
    private String dbSchema;

    @Override
    public void init(Config config, TaskContext context) throws Exception {
        successTopic = config.get("output.success.topic.name", "events_with_de_normalization");
        failedTopic = config.get("output.failed.topic.name", "events_failed_de_normalization");
        childData = (KeyValueStore<String, Child>) context.getStore("de-normalization");
        dbHost = config.get("db.host");
        dbPort = config.get("db.port");
        dbUserName = config.get("db.userName");
        dbPassword = config.get("db.password");
        dbSchema = config.get("db.schema");
    }

    @Override
    public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator) throws Exception {
        Map<String, Object> message = (Map<String, Object>) envelope.getMessage();
        Event event = new Event(message);
        Child child = getChild(event);
        populateTopic(collector, event, child);
    }

    private void populateTopic(MessageCollector collector, Event event, Child child) {
        String outputTopic;
        if(child.isProcessed()){
            childData.put(child.getUid(), child);
            outputTopic = successTopic;
        }
        else
            outputTopic = failedTopic;
        collector.send(new OutgoingMessageEnvelope(new SystemStream("kafka", outputTopic), event.getData()));
    }

    private Child getChild(Event event) {
        Child child = event.getChild();
        if(!child.isProcessed() && child.canBeProcessed()){
            Child cachedChild = childData.get(child.getUid());
            if(cachedChild == null)
                child.update(new Database(dbHost,dbPort,dbSchema,dbUserName,dbPassword));
            else
                child = cachedChild;
            event.update(child);
        }
        return child;
    }
}