package com.cybersim.scoringservice.workflow;
import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.store.*;
import com.cybersim.shared.dto.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class ScoringStageProcessorTest {
    static final UUID S=UUID.randomUUID(),R=UUID.randomUUID(),T=UUID.fromString("00000000-0000-0000-0000-000000000101"),V=UUID.randomUUID(),D=UUID.randomUUID(),M=UUID.randomUUID(),X=UUID.randomUUID();
    @Test void scoresDurableRoundEntitiesOnceAndCalculatesCompletion(){
        MemoryStore store=new MemoryStore(); ScoringStageProcessor p=new ScoringStageProcessor(store); Instant n=Instant.now();
        SimulationResponse sim=new SimulationResponse(S,"s",TargetMode.INTERNAL_SANDBOX,T,"RUNNING",1,3,60,2,"LOW",true,0,0,0,50,null,List.of(),n,null);
        VulnerabilityResponse v=new VulnerabilityResponse(V,S,R,T,"f","d","AUTHENTICATION","HIGH","OPEN","e","/demo","steps","fix",UUID.randomUUID(),null,n,n,null);
        DetectionEventResponse d=new DetectionEventResponse(D,S,R,T,"RED_TEAM_FINDING","detection.created","HIGH","m",null,V,Map.of(),n);
        RemediationResponse m=new RemediationResponse(M,S,R,V,D,UUID.randomUUID(),T,"AUTH_REQUIRED","p","APPLIED","ok",n,n,n,n,null,null);
        VerificationResponse x=new VerificationResponse(X,S,R,V,M,T,"PASSED","ok",n);
        RoundCompletionRequest first=p.score(S,R,sim,List.of(v),List.of(d),List.of(m),List.of(x));
        RoundCompletionRequest retry=p.score(S,R,sim,List.of(v),List.of(d),List.of(m),List.of(x));
        assertThat(first.newFindingsCount()).isEqualTo(1);assertThat(first.remediatedFindingsCount()).isEqualTo(1);assertThat(first.verifiedFixesCount()).isEqualTo(1);
        assertThat(first.redTeamScore()).isEqualTo(50);assertThat(first.blueTeamScore()).isEqualTo(135);assertThat(first.riskScoreAfter()).isZero();assertThat(first.allCriticalAndHighFixed()).isTrue();
        assertThat(retry).isEqualTo(first);assertThat(store.events).hasSize(6);
    }
    static class MemoryStore implements ScoreEventStore{
        final List<ScoreEventRecord> events=new ArrayList<>();
        public ScoreAppendResult append(ScoreEventRecord e){var old=findBySimulationIdAndSourceEventId(e.simulationId(),e.sourceEventId());if(old.isPresent())return new ScoreAppendResult(old.get(),false);events.add(e);return new ScoreAppendResult(e,true);}
        public Optional<ScoreEventRecord> findBySimulationIdAndSourceEventId(UUID s,UUID e){return events.stream().filter(x->s.equals(x.simulationId())&&e.equals(x.sourceEventId())).findFirst();}
        public List<ScoreEventRecord> findBySimulationId(UUID s){return events.stream().filter(x->s.equals(x.simulationId())).toList();}
    }
}
