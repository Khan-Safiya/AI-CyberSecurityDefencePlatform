package com.cybersim.scoringservice.messaging;
import com.cybersim.scoringservice.persistence.ConsumedMessageStore;
import com.cybersim.scoringservice.workflow.*;
import com.cybersim.shared.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
class ScoringStageConsumerTest {
 @Test void duplicateDeliveryCompletesRoundOnce(){
  UUID mid=UUID.randomUUID(),sid=UUID.randomUUID(),rid=UUID.randomUUID();ConsumedMessageStore inbox=mock(ConsumedMessageStore.class);ScoringStageProcessor p=mock(ScoringStageProcessor.class);ScoringWorkflowClient c=mock(ScoringWorkflowClient.class);
  when(inbox.contains(mid)).thenReturn(false,true);when(c.findings(sid)).thenReturn(List.of());when(c.detections(sid)).thenReturn(List.of());when(c.remediations(sid)).thenReturn(List.of());when(c.verifications(sid)).thenReturn(List.of());
  SimulationResponse sim=mock(SimulationResponse.class);when(c.simulation(sid)).thenReturn(sim);RoundCompletionRequest result=new RoundCompletionRequest(0,0,0,50,50,0,0,true,false,true,false);when(p.score(sid,rid,sim,List.of(),List.of(),List.of(),List.of())).thenReturn(result);
  Message m=MessageBuilder.withBody(("{\"roundId\":\""+rid+"\"}").getBytes(StandardCharsets.UTF_8)).setMessageId(mid.toString()).setHeader("eventType",ScoringRabbitConfiguration.ROUTING_KEY).setHeader("simulationId",sid.toString()).build();ScoringStageConsumer consumer=new ScoringStageConsumer(inbox,p,c,new ObjectMapper());consumer.consume(m);consumer.consume(m);
  verify(c,times(1)).completeRound(sid,rid,result);verify(inbox,times(1)).record(mid);
 }
}
