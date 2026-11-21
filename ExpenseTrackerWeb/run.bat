@echo off
if not exist out mkdir out
dir /s /b src\*.java > files.txt
javac -d out @files.txt
java -cp out com.expensetracker.Main %*
