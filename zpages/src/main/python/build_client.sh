#!/bin/bash

mkdir -p ../../generated/main/python
python -m grpc_tools.protoc -I../proto --python_out=../../generated/main/python --grpc_python_out=../../generated/main/python ../proto/empty_message.proto ../proto/expvar.proto ../proto/grpc_status.proto ../proto/message_set.proto ../proto/simple_service.proto

