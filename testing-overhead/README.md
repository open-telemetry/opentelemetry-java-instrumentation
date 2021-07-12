
# Overhead tests

This is a work in progress. 

This directory will contain tools and utilities
that help us to measure the performance overhead introduced by 
the agent and to measure how this overhead changes over time.

## Contents

`spring-petclinic-rest` - This is the sample app that we instrument and test against. It is included as 
a git submodule (see below).

## Config

tbd

## Setup and Usage

First, init update the submodules in order to pick up the `spring-petclinic-rest` app.
Once we have an established base image published for `spring-petclinic-rest`, this step
can be avoided by most users.

```
$ git submodule init
$ git submodule update
$ docker build -f Dockerfile-petclinic-base .
```

Remaining usage TBD.