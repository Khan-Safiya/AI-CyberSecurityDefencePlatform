package com.cybersim.scoringservice.workflow;
import com.cybersim.shared.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;
@Component
class HttpScoringWorkflowClient implements ScoringWorkflowClient {
    private final RestClient simulation,vulnerability,detection,remediation,verification;
    HttpScoringWorkflowClient(@Value("${scoring.simulation-base-url}")String s,@Value("${scoring.vulnerability-base-url}")String v,
      @Value("${scoring.detection-base-url}")String d,@Value("${scoring.remediation-base-url}")String r,
      @Value("${scoring.verification-base-url}")String x,@Value("${scoring.service-auth-token}")String token){
        simulation=client(s,token); vulnerability=client(v,token); detection=client(d,token); remediation=client(r,token); verification=client(x,token);
    }
    public SimulationResponse simulation(UUID id){return simulation.get().uri("/simulations/{id}",id).retrieve().body(SimulationResponse.class);}
    public List<VulnerabilityResponse> findings(UUID id){return list(vulnerability,"/simulations/{id}/vulnerabilities",id);}
    public List<DetectionEventResponse> detections(UUID id){return list(detection,"/simulations/{id}/detections",id);}
    public List<RemediationResponse> remediations(UUID id){return list(remediation,"/simulations/{id}/remediations",id);}
    public List<VerificationResponse> verifications(UUID id){return list(verification,"/simulations/{id}/verifications",id);}
    public void completeRound(UUID sid,UUID rid,RoundCompletionRequest body){simulation.post().uri("/simulations/{sid}/rounds/{rid}/complete",sid,rid).body(body).retrieve().toBodilessEntity();}
    private static <T> List<T> list(RestClient c,String uri,UUID id){List<T> values=c.get().uri(uri,id).retrieve().body(new ParameterizedTypeReference<>(){});return values==null?List.of():values;}
    private static RestClient client(String url,String token){return RestClient.builder().baseUrl(url).defaultHeader("X-Service-Token",token).build();}
}
