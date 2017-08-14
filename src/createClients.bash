#!/bin/bash

numberOfClients=$1
serverIP=$2
serverPort=$3

for((i=0;i<numberOfClients;i++))
do
    java cs455.scaling.client.Client $serverIP $serverPort 4 &
done
