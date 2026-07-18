FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /ngelmak

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package

FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /ngelmak

USER root
RUN apk add --no-cache curl
USER 1000:1000

COPY --from=builder /ngelmak/target/ngelmak-core-*.jar ./ngelmakapp.jar

ENTRYPOINT ["java", "-jar", "ngelmakapp.jar"]