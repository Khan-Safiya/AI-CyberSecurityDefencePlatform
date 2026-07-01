package com.cybersim.scoringservice.workflow;

import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.store.ScoreAppendResult;
import com.cybersim.scoringservice.store.ScoreEventStore;
import com.cybersim.shared.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ScoringStageProcessor {
    private final ScoreEventStore store;
    public ScoringStageProcessor(ScoreEventStore store){this.store=store;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public RoundCompletionRequest score(UUID simulationId,UUID roundId,SimulationResponse simulation,
      List<VulnerabilityResponse> findings,List<DetectionEventResponse> detections,
      List<RemediationResponse> remediations,List<VerificationResponse> verifications){
        List<VulnerabilityResponse> rf=findings.stream().filter(x->simulationId.equals(x.simulationId())&&roundId.equals(x.roundId())).toList();
        List<DetectionEventResponse> rd=detections.stream().filter(x->simulationId.equals(x.simulationId())&&roundId.equals(x.roundId())).toList();
        List<RemediationResponse> rr=remediations.stream().filter(x->simulationId.equals(x.simulationId())&&roundId.equals(x.roundId())).toList();
        List<VerificationResponse> rv=verifications.stream().filter(x->simulationId.equals(x.simulationId())&&roundId.equals(x.roundId())).toList();

        rf.forEach(x->append(simulationId,roundId,x.id(),"RED_"+x.severity()+"_FINDING",x.discoveredByAgentId()));
        rd.forEach(x->append(simulationId,roundId,x.id(),"BLUE_VALID_DETECTION",null));
        rr.forEach(x->{
            append(simulationId,roundId,x.id(),"BLUE_CORRECT_TRIAGE",x.agentId());
            append(simulationId,roundId,x.id(),"BLUE_VALID_REMEDIATION_PROPOSAL",x.agentId());
            if("APPLIED".equals(x.status())||"VERIFIED".equals(x.status())) append(simulationId,roundId,x.id(),"BLUE_PATCH_APPLIED",x.agentId());
            else if("FAILED".equals(x.status())) append(simulationId,roundId,x.id(),"BLUE_FAILED_PATCH",x.agentId());
        });
        rv.stream().filter(x->"PASSED".equals(x.status())).forEach(x->append(simulationId,roundId,x.id(),"BLUE_FIX_VERIFIED",null));

        List<ScoreEventRecord> all=store.findBySimulationId(simulationId);
        int red=Math.toIntExact(all.stream().filter(x->"RED".equals(x.team())).mapToLong(ScoreEventRecord::points).sum());
        int blue=Math.toIntExact(all.stream().filter(x->"BLUE".equals(x.team())).mapToLong(ScoreEventRecord::points).sum());
        int risk=Math.max(0,Math.min(100,50+red-blue));
        Set<UUID> passed=rv.stream().filter(x->"PASSED".equals(x.status())).map(VerificationResponse::vulnerabilityId).collect(java.util.stream.Collectors.toSet());
        boolean allHighFixed=rf.stream().filter(x->"HIGH".equals(x.severity())||"CRITICAL".equals(x.severity())).allMatch(x->passed.contains(x.id()));
        int remediated=(int)rr.stream().filter(x->"APPLIED".equals(x.status())||"VERIFIED".equals(x.status())).count();
        int verified=(int)rv.stream().filter(x->"PASSED".equals(x.status())).count();
        return new RoundCompletionRequest(rf.size(),remediated,verified,simulation.finalRiskScore(),risk,red,blue,
                allHighFixed,false,true,false);
    }

    private void append(UUID simulationId,UUID roundId,UUID entityId,String rule,UUID agentId){
        if("RED_INFO_FINDING".equals(rule)) return;
        UUID source=UUID.nameUUIDFromBytes((entityId+":"+rule).getBytes(StandardCharsets.UTF_8));
        ScoreEventCreateRequest request=new ScoreEventCreateRequest(simulationId,roundId,source,rule,agentId);
        ScoreAppendResult result=store.append(ScoreEventRecord.from(request));
        if(!result.created()&&!result.event().representsSameRequest(request)) throw new IllegalStateException("Score source conflict: "+source);
    }
}
