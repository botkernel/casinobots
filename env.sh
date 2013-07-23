#!/bin/bash
#
# Source this env script using:
# . env.sh
#
# to configure a classpath including all dependencies.
#

export CLASSPATH=${CLASSPATH}:../jReddit/dist/jreddit.jar
export CLASSPATH=${CLASSPATH}:../jReddit/deps/json-simple-1.1.1.jar
export CLASSPATH=${CLASSPATH}:../botkernel/deps/hotkernel.jar
export CLASSPATH=${CLASSPATH}:../simple-cards/deps/simple-cards.jar


