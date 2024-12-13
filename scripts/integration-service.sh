#!/bin/bash

. ./lib.sh

CLASSPATH=
for i in ../libs/*.jar; do
    CLASSPATH=${CLASSPATH}:$i
done

PORT_FILE="../tmp/.port-number"
MAIN_CLASS=org.dreamwork.integration.bootloader.AppBootable
APP_NAME="integration-projects"
JAVA_OPTS="-DAPP_NAME=${APP_NAME}"

case "$1" in
    update-framework)
        MODULES="integration-projects embedded-httpd-api embedded-httpd embedded-mqtt embedded-redis framework-management-kt integration-api integration-bootloader"
        for i in ${MODULES}; do
            update $i
            if [ $? -ne 0 ]; then
                exit -1
            fi
        done
        cd ${PROJECT_DIR}/modules/integration-projects
        mvn package
        exit $?
        ;;

    run)
        java ${JAVA_OPTS} -cp $CLASSPATH $MAIN_CLASS --verbose "--trace-prefix=org.dreamwork;org.dreamwork"
        ;;

    start)
        echo -n "starting integration framework ... "
        java ${JAVA_OPTS} -cp $CLASSPATH $MAIN_CLASS > ../logs/integrated-projects.log 2>&1 &
        if [ $? -eq 0 ]; then
            for ((i = 0; i < 60; i ++)); do
                if [ -f $PORT_FILE ]; then
                    success
                    exit 0
                fi
                sleep 1
            done
            fail
        else
            fail
        fi
        ;;

    stop|shutdown)
        echo -n "stopping integration framework ... "
        java -cp $CLASSPATH $MAIN_CLASS --shutdown > /dev/null 2>&1
        for ((i = 0; i < 60; i ++)); do
            ps -ef | grep java | grep ${APP_NAME} | grep -v grep > /dev/null
            if [ $? -ne 0 ]; then
                success
                exit 0
            fi
            sleep 1
        done
        fail
        ;;

    *)
        echo "Usage: $0 run|start|stop|shutdown"
        ;;
esac