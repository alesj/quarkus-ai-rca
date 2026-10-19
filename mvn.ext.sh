#!/bin/bash -v

./mvnw --projects :quarkus-ai-rca-ext --also-make $*
