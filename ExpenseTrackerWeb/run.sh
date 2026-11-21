#!/bin/sh
# Build and run: ./run.sh [port]
mkdir -p out
javac -d out $(find src -name "*.java") && java -cp out com.expensetracker.Main "$@"
