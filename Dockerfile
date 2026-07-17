FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests use Testcontainers, which needs a Docker daemon that isn't available inside this build
# stage; the full suite already runs in CI/local dev, so skip it here.
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/school-app-backend.jar app.jar
EXPOSE 8080
# Startup-speed tuning for Render's constrained free-tier CPU: SerialGC is far cheaper to warm up
# than the default G1 for a small heap, TieredStopAtLevel=1 skips C2 JIT compilation (a steady-state
# throughput optimization we don't need for a short cold-start window), and MaxRAMPercentage gives
# the JVM a sane heap size relative to the container's actual memory instead of guessing.
#
# java.security.egd forces SecureRandom onto the non-blocking /dev/urandom source instead of the
# JVM default (blocking /dev/random on Linux). BCryptPasswordEncoder seeds a SecureRandom at
# construction time, and this minimal Alpine container has very little system entropy to draw
# from — without this flag, startup hangs indefinitely waiting on entropy right as Spring Security
# wires up the AuthenticationManager, which is exactly the symptom that was timing out deploys.
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
