#!/bin/bash
java -Xmx384M -Xms64M -XXaltjvm=dcevm -javaagent:hotswap-agent-1.1.0-SNAPSHOT.jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 -jar Botcoins.jar
