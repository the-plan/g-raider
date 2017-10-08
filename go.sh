#!/usr/bin/env bash

#REDIS_RECORDS_KEY="bsg-the-plan" SERVICE_NAME="R" SERVICE_PORT=9090 PORT=9090 java -jar target/g-raider-1.0-SNAPSHOT-fat.jar

#REDIS_RECORDS_KEY="bsg-the-plan" SERVICE_NAME="R" SERVICE_PORT=9999 PORT=9999 java  -jar target/g-raider-1.0-SNAPSHOT-fat.jar&


startPort=9090

for i in `seq 21 26`;
do
    resultat=$(($startPort+$i))
    REDIS_RECORDS_KEY="bsg-the-plan" SERVICE_NAME="R" SERVICE_PORT=$resultat PORT=$resultat java  -jar target/g-raider-1.0-SNAPSHOT-fat.jar&
done

