#!/bin/bash
CDIR=$(realpath `pwd`)
#sudo install git maven

. ./lib.sh
DIST_DIR=${DEFAULT_DIST_DIR}
if [ "$1" == "" ]; then
    warn "dist dir is missing, using default location: ${DEFAULT_DIST_DIR}"
else
    DIST_DIR=$1
    if [ ! -d ${DIST_DIR} ]; then
        mkdir -p ${DIST_DIR}
        cd ${DIST_DIR}
        DIST_DIR=$(realpath `pwd`)
        if [ $? -ne 0 ]; then
            error "cannot create dir: ${DIST_DIR}"
            exit -1
        fi
    fi
    echo "the integration framework will be installed at: ${DIST_DIR}"
fi
cd $CDIR

if [ ! -d ${PROJECT_DIR} ]; then
    mkdir -p ${PROJECT_DIR}

    cd ${PROJECT_DIR}
    checkout https://gitee.com/hothink/integration-projects.git ${IF_VER}
    checkout https://gitee.com/hothink/embedded-httpd-api.git ${IF_VER}
    checkout https://gitee.com/hothink/embedded-httpd.git ${IF_VER}
    checkout https://gitee.com/hothink/embedded-mqtt.git ${IF_VER}
    checkout https://gitee.com/hothink/embedded-redis.git ${IF_VER}
    checkout https://gitee.com/hothink/framework-management-kt.git ${IF_VER}
    checkout https://gitee.com/hothink/integration-api.git ${IF_VER}
    checkout https://gitee.com/hothink/integration-bootloader.git ${IF_VER}
    checkout https://gitee.com/hothink/oxygen-monitoring.git ${OXY_VER}
    checkout https://gitee.com/hothink/guanghelin-intergration.git
    checkout https://gitee.com/hothink/integrated-device-manager-api.git
    checkout https://gitee.com/hothink/integrated-device-manager.git
    checkout https://gitee.com/hothink/starwsn-integration.git
else
    cd ${PROJECT_DIR}
    cd integration-projects && git pull
    cd ../embedded-httpd-api && git pull
    cd ../embedded-httpd && git pull
    cd ../embedded-mqtt && git pull
    cd ../embedded-redis && git pull
    cd ../integration-projects && mvn clean

    cd ../framework-management-kt && git pull
    cd ../integrated-device-manager-api && git pull
    cd ../integrated-device-manager && git pull

fi

cd ${PROJECT_DIR}
# 编译集成框架
cd integration-projects
mvn install && cd ..

# 编译集成框架管理模块
cd framework-management-kt
mvn clean && mvn package && cd ..

# 编译通用设备管理模块
cd integrated-device-manager-api
mvn clean && mvn install && cd ..
cd integrated-device-manager
mvn clean && mvn package && cd ..

# 编译广合霖一键报警器
cd guanghelin-intergration
mvn clean && mvn package && cd ..

# 编译恒星井盖
cd starwsn-integration
mvn clean && mvn package && cd ..

# 编译晶合氧气
cd oxygen-monitoring
mvn clean && mvn package && cd ..

# 开始部署
mkdir ${DIST_DIR} && cd ${DIST_DIR}
mkdir bin conf conf.d database ext-services internal libs logs modules tmp work
cd libs
# 通用库
ln -s ~/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/9.0.86/tomcat-embed-core-9.0.86.jar
ln -s ~/.m2/repository/org/apache/tomcat/tomcat-annotations-api/9.0.86/tomcat-annotations-api-9.0.86.jar
ln -s ~/.m2/repository/org/apache/tomcat/embed/tomcat-embed-logging-juli/9.0.0.M6/tomcat-embed-logging-juli-9.0.0.M6.jar
ln -s ~/.m2/repository/org/apache/tomcat/tomcat-jsp-api/9.0.86/tomcat-jsp-api-9.0.86.jar
ln -s ~/.m2/repository/org/apache/tomcat/tomcat-el-api/9.0.86/tomcat-el-api-9.0.86.jar
ln -s ~/.m2/repository/org/apache/tomcat/tomcat-servlet-api/9.0.86/tomcat-servlet-api-9.0.86.jar
ln -s ~/.m2/repository/org/apache/tomcat/embed/tomcat-embed-el/9.0.86/tomcat-embed-el-9.0.86.jar
ln -s ~/.m2/repository/org/apache/tomcat/embed/tomcat-embed-jasper/9.0.86/tomcat-embed-jasper-9.0.86.jar
ln -s ~/.m2/repository/org/eclipse/jdt/ecj/3.26.0/ecj-3.26.0.jar
ln -s ~/.m2/repository/org/apache/tomcat/embed/tomcat-embed-websocket/9.0.86/tomcat-embed-websocket-9.0.86.jar
ln -s ~/.m2/repository/redis/clients/jedis/3.4.1/jedis-3.4.1.jar
ln -s ~/.m2/repository/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar
ln -s ~/.m2/repository/org/dreamwork/dreamwork-application-bootloader/1.1.1/dreamwork-application-bootloader-1.1.1.jar
ln -s ~/.m2/repository/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar
ln -s ~/.m2/repository/org/apache/commons/commons-dbcp2/2.1.1/commons-dbcp2-2.1.1.jar
ln -s ~/.m2/repository/org/apache/commons/commons-pool2/2.4.2/commons-pool2-2.4.2.jar
ln -s ~/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar
ln -s ~/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.6.21/kotlin-stdlib-1.6.21.jar
ln -s ~/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-common/1.6.21/kotlin-stdlib-common-1.6.21.jar
ln -s ~/.m2/repository/org/jetbrains/annotations/13.0/annotations-13.0.jar
ln -s ~/.m2/repository/org/xerial/sqlite-jdbc/3.41.2.2/sqlite-jdbc-3.41.2.2.jar
ln -s ~/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar
ln -s ~/.m2/repository/org/checkerframework/checker-qual/3.31.0/checker-qual-3.31.0.jar
ln -s ~/.m2/repository/org/apache/mina/mina-core/2.2.1/mina-core-2.2.1.jar
ln -s ~/.m2/repository/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar
ln -s ~/.m2/repository/org/slf4j/slf4j-log4j12/1.7.25/slf4j-log4j12-1.7.25.jar
ln -s ~/.m2/repository/log4j/log4j/1.2.17/log4j-1.2.17.jar
ln -s ~/.m2/repository/org/dreamwork/dreamwork-base/3.1.3/dreamwork-base-3.1.3.jar
ln -s ~/.m2/repository/org/dreamwork/tools/ok-http-wrapper/1.0.0/ok-http-wrapper-1.0.0.jar
ln -s ~/.m2/repository/com/squareup/okhttp3/okhttp/3.14.9/okhttp-3.14.9.jar
ln -s ~/.m2/repository/com/squareup/okio/okio/1.17.2/okio-1.17.2.jar
# 集成框架库
ln -s ${PROJECT_DIR}/integration-bootloader/target/integration-framework-${IF_VER}.jar
ln -s ${PROJECT_DIR}/integration-api/target/integration-api-${IF_VER}.jar
ln -s ${PROJECT_DIR}/embedded-httpd/target/embedded-httpd-${IF_VER}.jar
ln -s ${PROJECT_DIR}/embedded-httpd-api/target/embedded-httpd-api-${IF_VER}.jar
ln -s ${PROJECT_DIR}/embedded-redis/target/embedded-redis-${IF_VER}.jar
ln -s ${PROJECT_DIR}/embedded-mqtt/target/embedded-mqtt-${IF_VER}.jar
ln -s ${PROJECT_DIR}/integrated-device-manager-api/target/integrated-device-manager-api-1.0.0.jar

# 通用配置文件
cd ../conf
ln -s ${PROJECT_DIR}/integration-bootloader/src/main/conf/integration.conf

# 模块配置文件
cd ../conf.d
cp ${PROJECT_DIR}/embedded-mqtt/src/main/conf.d/embedded-mqtt.conf .
cp ${PROJECT_DIR}/integration-bootloader/src/main/conf.d/database-provider.conf .
cp ${PROJECT_DIR}/embedded-redis/src/main/conf.d/embedded-redis.conf .

ln ${PROJECT_DIR}/embedded-httpd/src/main/conf.d/embedded-httpd.conf
ln ${PROJECT_DIR}/integration-bootloader/src/main/conf.d/discovery.conf
ln ${PROJECT_DIR}/integrated-device-manager/src/main/conf.d/device-management.conf
ln ${PROJECT_DIR}/integration-bootloader/src/main/conf.d/framework-manager.conf

ln ${PROJECT_DIR}/guanghelin-intergration/src/main/conf.d/guanghelin.conf
ln ${PROJECT_DIR}/starwsn-integration/src/main/conf.d/starwsn.conf
ln ${PROJECT_DIR}/oxygen-monitoring/src/main/conf.d/mine-star.conf

# 部署模块
cd ../modules
# 框架管理模块
mkdir -p framework-manager/libs && cd framework-manager/libs
ln -s ~/.m2/repository/org/dreamwork/dreamwork-websocket/2.1.1/dreamwork-websocket-2.1.1.jar
ln -s ${PROJECT_DIR}/framework-management-kt/target/framework-management-kt-${IF_VER}.jar
cd ..
ln -s ${PROJECT_DIR}/framework-management-kt/src/main/web-app

# 通用设备管理模块
cd ${DIST_DIR}/modules
mkdir -p device-management/libs && cd device-management/libs
ln -s ${PROJECT_DIR}/integrated-device-manager/target/integrated-device-manager-1.0.0.jar

# 广合霖一键报警器
cd ../../
mkdir -p guanghelin/libs && cd guanghelin/libs
ln -s ${PROJECT_DIR}/guanghelin-intergration/target/guanghelin-integration-1.0.0.jar

# 恒星井盖
cd ../../
mkdir -p starwsn/libs && cd starwsn/libs
ln -s ${PROJECT_DIR}/starwsn-integration/target/starwsn-1.0.0.jar
ln -s ~/.m2/repository/com/starwsn/starwsn-mqtt-protocol/1.2.6/starwsn-mqtt-protocol-1.2.6.jar

# 晶合氧气
cd ../../
mkdir -p mine-star/libs && cd mine-star/libs
ln -s ${PROJECT_DIR}/oxygen-monitoring/target/oxygen-monitoring-${OXY_VER}.jar
ln -s ~/.m2/repository/org/quartz-scheduler/quartz/2.3.2/quartz-2.3.2.jar
ln -s ~/.m2/repository/com/mchange/c3p0/0.9.5.4/c3p0-0.9.5.4.jar
ln -s ~/.m2/repository/com/mchange/mchange-commons-java/0.2.15/mchange-commons-java-0.2.15.jar
ln -s ~/.m2/repository/com/zaxxer/HikariCP-java7/2.4.13/HikariCP-java7-2.4.13.jar

echo -n "everything is "
success

echo "please EDIT "
echo "  - ${DIST_DIR}/conf.d/embedded-mqtt.conf"
echo "and "
echo "  - ${DIST_DIR}/conf.d/database-provider.conf"
echo "to apply the actual database and/or redis."

cd ${DIST_DIR}/bin
chmod +x ${PROJECT_DIR}/integration-projects/scripts/integration-service.sh
ln -s ${PROJECT_DIR}/integration-projects/scripts/integration-service.sh