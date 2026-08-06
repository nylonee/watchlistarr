FROM hseeberger/scala-sbt:eclipse-temurin-11.0.14.1_1.6.2_2.13.8 AS build

WORKDIR /app

# Copy project definition first for caching
COPY project ./project
COPY build.sbt ./

# Copy the rest of the source code
COPY . .

RUN sbt update compile stage
RUN chmod a+rx /app/target/universal/stage/bin/*

FROM eclipse-temurin:11-jre-jammy

WORKDIR /app

COPY --from=build /app/target/universal/stage /app

COPY --chmod=755 docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
