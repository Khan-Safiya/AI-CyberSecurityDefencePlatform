package com.cybersim.scoringservice.messaging;
import com.cybersim.scoringservice.persistence.ConsumedMessageStore;
import com.cybersim.scoringservice.workflow.*;
import com.cybersim.shared.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;
@Component
@ConditionalOnProperty(name="scoring.consumer.enabled",havingValue="true",matchIfMissing=true)
public class ScoringStageConsumer {
    private static final TypeReference<Map<String,Object>> TYPE=new TypeReference<>(){};
    private final ConsumedMessageStore inbox; private final ScoringStageProcessor processor;
    private final ScoringWorkflowClient client; private final ObjectMapper mapper;
    public ScoringStageConsumer(ConsumedMessageStore i,ScoringStageProcessor p,ScoringWorkflowClient c,ObjectMapper m){inbox=i;processor=p;client=c;mapper=m;}
    @RabbitListener(queues=ScoringRabbitConfiguration.QUEUE)
    public void consume(Message message){
        UUID mid=uuid(message.getMessageProperties().getMessageId(),"messageId"); if(inbox.contains(mid))return;
        if(!ScoringRabbitConfiguration.ROUTING_KEY.equals(header(message,"eventType")))throw new IllegalArgumentException("Unexpected scoring event type");
        UUID sid=uuid(header(message,"simulationId"),"simulationId"); UUID rid=uuid(String.valueOf(payload(message).get("roundId")),"roundId");
        RoundCompletionRequest result=processor.score(sid,rid,client.simulation(sid),client.findings(sid),client.detections(sid),client.remediations(sid),client.verifications(sid));
        client.completeRound(sid,rid,result); inbox.record(mid);
    }
    private Map<String,Object> payload(Message m){try{return mapper.readValue(m.getBody(),TYPE);}catch(IOException e){throw new IllegalArgumentException("Scoring payload is not valid JSON",e);}}
    private String header(Message m,String n){Object v=m.getMessageProperties().getHeaders().get(n);if(v==null||v.toString().isBlank())throw new IllegalArgumentException("Missing scoring header: "+n);return v.toString();}
    private UUID uuid(String v,String n){if(v==null||v.isBlank()||"null".equals(v))throw new IllegalArgumentException("Missing scoring field: "+n);try{return UUID.fromString(v);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid scoring UUID: "+n,e);}}
}
