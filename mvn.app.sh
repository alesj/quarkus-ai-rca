#!/bin/bash -v

./mvnw --projects :quarkus-ai-rca-app --also-make $*
