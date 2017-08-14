#!/bin/bash
for pid in $(ps -ef | grep "java cs455.scaling.client.Client" | awk '{print $2}'); do kill -9 $pid; done
