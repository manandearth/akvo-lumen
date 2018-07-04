#!/usr/bin/env bash
set -eu

function log {
   echo "$(date +"%T") - INFO - $*"
}

export PROJECT_NAME=akvo-lumen

if [ -z "$TRAVIS_COMMIT" ]; then
    export TRAVIS_COMMIT=local
fi

log Pulling backend-base
docker pull akvo/akvo-lumen-backend-base:dan-local
log Pulling backend-dev
docker pull akvo/akvo-lumen-backend-dev:dan-local
log Pulling e2e-tests
docker pull akvo/akvo-lumen-e2e-tests:dan-local
log Pulling redis
docker pull redis:3.2.9
log Pulling keycloak
docker pull jboss/keycloak:3.4.3.Final
log Pulling postgis
docker pull mdillon/postgis:9.6
log Pulling akvo-maps
docker pull akvo/akvo-maps:da60926a
log Pulling client-dev mhart/alpine-node
docker pull mhart/alpine-node:8
log Pulling client-prod nginx
docker pull nginx:1.13.8-alpine

log Running Backend unit tests and building uberjar
docker run --env-file=.env -v "$HOME/.m2:/home/akvo/.m2" -v "$(pwd)/backend:/app" akvo/akvo-lumen-backend-dev:dan-local /app/run-as-user.sh lein "do" test, eastwood, uberjar

cp backend/target/uberjar/akvo-lumen.jar backend

log Creating Production Backend image
docker build --rm=false -t eu.gcr.io/${PROJECT_NAME}/lumen-backend:${TRAVIS_COMMIT} ./backend
docker tag eu.gcr.io/${PROJECT_NAME}/lumen-backend:${TRAVIS_COMMIT} eu.gcr.io/${PROJECT_NAME}/lumen-backend:develop

#rm backend/akvo-lumen.jar

log Building container to run the client unit tests
docker build --rm=false -t akvo-lumen-client-dev:develop client -f client/Dockerfile-dev

log Running Client linting, unit tests and creating production assets
docker run --env-file=.env -v "$(pwd)/client:/lumen" --rm=false -t akvo-lumen-client-dev:develop /lumen/run-as-user.sh /lumen/ci-build.sh

log Creating Production Client image
docker build --rm=false -t eu.gcr.io/${PROJECT_NAME}/lumen-client:${TRAVIS_COMMIT} ./client
docker tag eu.gcr.io/${PROJECT_NAME}/lumen-client:${TRAVIS_COMMIT} eu.gcr.io/${PROJECT_NAME}/lumen-client:develop

log Creating Production Windshaft image
docker build --rm=false -t eu.gcr.io/${PROJECT_NAME}/lumen-maps:${TRAVIS_COMMIT} ./windshaft
docker tag eu.gcr.io/${PROJECT_NAME}/lumen-maps:${TRAVIS_COMMIT} eu.gcr.io/${PROJECT_NAME}/lumen-maps:develop

log Starting Docker Compose environment
docker-compose -p akvo-lumen-ci -f docker-compose.yml -f docker-compose.ci.yml up --no-color -d --build

bash ci/helpers/wait-for-docker-compose-to-start.sh

log Running Backend functional tests
docker-compose -p akvo-lumen-ci -f docker-compose.yml -f docker-compose.ci.yml run --no-deps backend-functional-tests /app/import-and-run.sh functional-and-seed

log Running the end to end tests against local Docker Compose Environment
./ci/e2e-test.sh akvolumenci http://t1.lumen.local/

log Done
#docker-compose -p akvo-lumen-ci -f docker-compose.yml -f docker-compose.ci.yml down
