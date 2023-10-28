FROM hseeberger/scala-sbt:eclipse-temurin-11.0.14.1_1.6.2_2.13.8 as build

WORKDIR /app

COPY / /
RUN sbt update

COPY .. .
RUN sbt compile stage

FROM openjdk:11-jre-slim

WORKDIR /app

COPY --from=build /app/target/universal/stage /app

COPY docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
