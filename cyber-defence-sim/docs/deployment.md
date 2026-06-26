# Deployment

The local deployment target is Docker Compose. Production deployment should split services into independently deployed containers with per-service database schemas, secret management, service-to-service authentication, centralized logging, and broker dead-letter queues.
