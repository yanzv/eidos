#!/usr/bin/env bash

echo "Starting Eidos"

SLEEP=30
DEFAULT_THREADS=4
DEFAULT_EIDOS_MEMORY="-Xmx20g"

THREADS="${EIDOS_THREADS:-$DEFAULT_THREADS}"
EIDOS_MEMORY="${EIDOS_MEMORY:-DEFAULT_EIDOS_MEMORY}"
export _JAVA_OPTIONS=${DEFAULT_EIDOS_MEMORY}
export JAVA_OPTS=${DEFAULT_EIDOS_MEMORY}

DATA_DIR="${DATA_DIR:-/opt/app/data}"

mkdir -p "$DATA_DIR/output/done"
mkdir -p "$DATA_DIR/input/done"
mkdir -p "$DATA_DIR/input/kafka/done"
mkdir -p "$DATA_DIR/documents"
mkdir -p "$DATA_DIR/ontologies"
mkdir -p "$DATA_DIR/readings"

./bin/rest-producer-loop-app-2 "${DATA_DIR}/output" "${DATA_DIR}" "${DATA_DIR}/output/done" &
sleep 10
./bin/rest-consumer-loop-app-2 "${DATA_DIR}/input/kafka" "${DATA_DIR}/input" "${DATA_DIR}/input/kafka/done" "$DATA_DIR/documents" "$DATA_DIR/ontologies" "$DATA_DIR/readings" &
sleep 5
./bin/kafka-consumer-loop-app-2 &
sleep 5

# The container ends when eidos stops running.
while ! ./bin/eidos-loop-app-2 "${DATA_DIR}/input" "${DATA_DIR}/output" "${DATA_DIR}/input/done" "$DATA_DIR/documents" "$DATA_DIR/ontologies" "$DATA_DIR/readings" "${THREADS}"
do
  echo "It failed, so I am trying again in ${SLEEP} seconds."
  sleep ${SLEEP}
done
