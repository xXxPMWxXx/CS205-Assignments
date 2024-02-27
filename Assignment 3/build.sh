#!/usr/bin/env bash

# Check if the bin directory does not exist
if [ ! -d "./bin" ]; then
    # Create the bin directory
    mkdir ./bin
fi
javac -d ./bin restaurant.java