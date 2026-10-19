#!/bin/bash -v

./mvnw --projects :quarkus-ai-rca-core --also-make $*
