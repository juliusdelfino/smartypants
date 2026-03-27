#!/bin/bash

PID=$(ps -ef | grep SmartyPants | grep -v grep | awk '{print $2}')
kill -15 $PID