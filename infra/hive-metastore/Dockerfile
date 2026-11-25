# Hive Metastore 4.0.0 + PostgreSQL JDBC driver + S3A (MinIO) support.
# Jars are fetched at build time so nothing binary is committed.
FROM apache/hive:4.0.0

USER root
ARG PG_JDBC_VERSION=42.7.4
ARG HADOOP_AWS_VERSION=3.3.6
ARG AWS_SDK_BUNDLE_VERSION=1.12.367
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_JDBC_VERSION}/postgresql-${PG_JDBC_VERSION}.jar /opt/hive/lib/
ADD https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_AWS_VERSION}/hadoop-aws-${HADOOP_AWS_VERSION}.jar /opt/hive/lib/
ADD https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_BUNDLE_VERSION}/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar /opt/hive/lib/
RUN chmod 644 /opt/hive/lib/*.jar

COPY metastore-site.xml /opt/hive/conf/metastore-site.xml
USER hive
