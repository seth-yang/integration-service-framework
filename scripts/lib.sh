PROJECT_DIR=~/.projects/modules
DEFAULT_DIST_DIR=~/integration-services
# IF 是 Integration-Framework 的缩写
IF_VER="1.1.0"
OXY_VER="1.0.1"

function success () {
    echo -e "\033[1;32m [ ok ] \033[0m"
}

function fail () {
    echo -e "\033[1;31m [fail] \033[0m"
}

function warn () {
    echo -e "\033[;33m[warn] $1\033[0m"
}

function error () {
    echo -e "\033[1;31m [error] $1\033[0m"
}

function checkout () {
    if [ "$1" == "" ]; then
        echo -e "\033[1;31mproject url is missing, nothing todo.\033[0m"
    fi
    OPTS=""
    if [ -n "$2" ]; then
        OPTS="-b $2"
    fi
    echo -n "checking out $1 ..."
    git clone $OPTS $1 > /dev/null 2>&1
    ret=$?
    if [ $ret -eq 0 ]; then
        success
    else
        fail
    fi
}

function update () {
    if [ "$1" == "" ]; then
        echo -e "\033[1;31mmodule name is missing, nothing todo.\033[0m"
        exit -1
    fi
    if [ ! -d ${PROJECT_DIR}/module/$1 ]; then
        echo -e "033[1;31mUnknown module: $1\033[0m"
        exit -1
    fi

    echo -n "updating $1 ... "
    cd ${PROJECT_DIR}/modules/$1
    git pull > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        success
        exit 0
    fi
    exit -1
}