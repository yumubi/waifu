FROM gradle:8.10.2-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

FROM openjdk:21-slim
EXPOSE 8888
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/waifu.jar
ENV PORT=8888
ENTRYPOINT ["java", "-jar", "/app/waifu.jar"]
