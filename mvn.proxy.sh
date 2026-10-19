#!/bin/bash -v

./mvnw --projects :quarkus-ai-rca-proxy --also-make $*
