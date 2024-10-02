FROM alpine:3.15.0

ARG version=0.0.1

ARG influxUrl
ARG influxToken
ARG influxOrg
ARG influxBucket
ARG properties

RUN wget -P /etc/apk/keys/ https://cdn.azul.com/public_keys/alpine-signing@azul.com-5d5dc44c.rsa.pub
RUN echo "https://repos.azul.com/zulu/alpine" | tee -a /etc/apk/repositories
RUN apk update
RUN apk add --no-cache zulu21-jdk

ENV INFLUX_URL=${influxUrl}
ENV INFLUX_TOKEN=${influxToken}
ENV INFLUX_ORG=${influxOrg}
ENV INFLUX_BUCKET=${influxBucket}
ENV CONVERTER_PROPERTIES=${properties}

ENV SERVER_PORT=80

COPY build/libs/influx-writer-${version}.jar /app/health-influx-writer.jar

ENTRYPOINT ["java", "-jar", "/app/health-influx-writer.jar"]
