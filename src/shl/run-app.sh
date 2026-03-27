#!/bin/bash
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd "${DIR}"

set OLLAMA_API_KEY=
java -Xms128m -Xmx128m -Dapp.name="SmartyPants" -jar bin/smartypants-1.0.0.jar com.delfino.smartypants.Application --spring.config.location=./config/ 2>&1
