#
# This Docker file is based on the Oracle Java 8 Dockerfile
#
# https://github.com/dockerfile/java
# https://github.com/dockerfile/java/tree/master/oracle-java8
#

# Pull base image.
FROM dockerfile/ubuntu

# Define working directory.
WORKDIR /data

# Define default command.
# CMD ["bash"]

### Install Java ###
RUN \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

# Define JAVA_HOME
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle


### Install git ###
RUN \
  apt-get update && \
  apt-get install -y git

### Instal Soufflé ###
# requirements to build
RUN \
  apt-get update && \
  apt-get install -y \
  autoconf \
  automake \
  bison \
  build-essential \
  clang \
  doxygen \
  flex \
  g++ \
  libboost-all-dev \
  make \
  python

# config, build, and install
RUN \
  git clone git://github.com/souffle-lang/souffle.git
RUN \
  cd souffle && \
  ./bootstrap && \
  ./configure && \
  make

# testing
RUN \
  cd souffle && \
  make check
