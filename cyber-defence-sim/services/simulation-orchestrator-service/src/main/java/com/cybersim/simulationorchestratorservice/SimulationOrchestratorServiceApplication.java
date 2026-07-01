package com.cybersim.simulationorchestratorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimulationOrchestratorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulationOrchestratorServiceApplication.class, args);
    }
}
