package com.cybersim.notificationservice.security;

import com.cybersim.notificationservice.controller.NotificationController;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class, properties = {
        "service-jwt.secret=notification-test-secret-at-least-32-bytes",
        "service-jwt.issuer=cybersim-services-test"
})
@Import(NotificationSecurityConfiguration.class)
class NotificationSecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void webhookDeliveryStateRequiresNotificationServiceToken() throws Exception {
        mvc.perform(get("/webhooks/deliveries"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/webhooks/deliveries").header("Authorization", bearer("SERVICE_SCORING")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/webhooks/deliveries").header("Authorization",
                        "Bearer " + token("SERVICE_NOTIFICATION", "scoring-service")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/webhooks/deliveries").header("Authorization", bearer("SERVICE_NOTIFICATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DELIVERED"));
    }

    @Test
    void internalNotificationsRequireNotificationServiceToken() throws Exception {
        mvc.perform(post("/internal/notifications"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/notifications").header("Authorization", bearer("SERVICE_NOTIFICATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void unsafeWebhookDestinationsAreRejectedBeforeDelivery() throws Exception {
        mvc.perform(post("/webhooks/deliveries")
                        .header("Authorization", bearer("SERVICE_NOTIFICATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event": "assessment.completed",
                                  "destinationUrl": "http://127.0.0.1/admin"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsafe webhook destination: destination URL must use HTTPS and include a hostname"));
    }

    @Test
    void safeWebhookDestinationIsQueued() throws Exception {
        mvc.perform(post("/webhooks/deliveries")
                        .header("Authorization", bearer("SERVICE_NOTIFICATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event": "assessment.completed",
                                  "destinationUrl": "https://hooks.example.com/cybersim"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.event").value("assessment.completed"))
                .andExpect(jsonPath("$.destinationHost").value("hooks.example.com"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    private String bearer(String role) {
        return "Bearer " + token(role, "notification-service");
    }

    private String token(String role, String audience) {
        return ServiceJwtSupport.issuer("notification-test-secret-at-least-32-bytes",
                "cybersim-services-test", "test-worker", role, audience).issue();
    }
}
