FROM akvo/akvo-lumen-backend-base:dan-local

WORKDIR /app

COPY akvo-lumen.jar /app/akvo-lumen.jar

COPY maybe-import-and-java-jar.sh /app/maybe-import-and-java-jar.sh
RUN chmod 777 /app/maybe-import-and-java-jar.sh
COPY wait-for-dependencies.sh /app/wait-for-dependencies.sh
RUN chmod 777 /app/wait-for-dependencies.sh

CMD ./maybe-import-and-java-jar.sh
