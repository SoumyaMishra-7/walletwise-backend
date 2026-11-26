# Architecture

Nginx routes authentication traffic to `auth-service` and balances transaction traffic between `finance-1` and `finance-2`. Both independently deployable Spring Boot services persist to MongoDB. Auth stores BCrypt password hashes and signs expiring HMAC-SHA256 JWTs; Finance verifies those tokens locally.
