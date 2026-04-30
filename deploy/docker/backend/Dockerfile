FROM maven:3.8.8-eclipse-temurin-8 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY srmp-common/pom.xml srmp-common/pom.xml
COPY srmp-web/pom.xml srmp-web/pom.xml
COPY srmp-security/pom.xml srmp-security/pom.xml
COPY srmp-tenant/pom.xml srmp-tenant/pom.xml
COPY srmp-base/pom.xml srmp-base/pom.xml
COPY srmp-road-asset/pom.xml srmp-road-asset/pom.xml
COPY srmp-inspection/pom.xml srmp-inspection/pom.xml
COPY srmp-disease/pom.xml srmp-disease/pom.xml
COPY srmp-assessment/pom.xml srmp-assessment/pom.xml
COPY srmp-gis/pom.xml srmp-gis/pom.xml
COPY srmp-import/pom.xml srmp-import/pom.xml
COPY srmp-file/pom.xml srmp-file/pom.xml
COPY srmp-agent/pom.xml srmp-agent/pom.xml
COPY srmp-dashboard/pom.xml srmp-dashboard/pom.xml
COPY srmp-admin/pom.xml srmp-admin/pom.xml

RUN mvn -pl srmp-admin -am dependency:go-offline -DskipTests

COPY srmp-common srmp-common
COPY srmp-web srmp-web
COPY srmp-security srmp-security
COPY srmp-tenant srmp-tenant
COPY srmp-base srmp-base
COPY srmp-road-asset srmp-road-asset
COPY srmp-inspection srmp-inspection
COPY srmp-disease srmp-disease
COPY srmp-assessment srmp-assessment
COPY srmp-gis srmp-gis
COPY srmp-import srmp-import
COPY srmp-file srmp-file
COPY srmp-agent srmp-agent
COPY srmp-dashboard srmp-dashboard
COPY srmp-admin srmp-admin

RUN mvn -pl srmp-admin -am package -DskipTests

FROM eclipse-temurin:8-jre

WORKDIR /app
COPY --from=builder /workspace/srmp-admin/target/srmp-admin-1.0.0.jar /app/srmp-admin.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.net.useSystemProxies=false -Dhttp.nonProxyHosts='localhost|127.*|[::1]|postgres|redis|minio|backend|frontend' -Dhttps.nonProxyHosts='localhost|127.*|[::1]|postgres|redis|minio|backend|frontend' -jar /app/srmp-admin.jar"]
