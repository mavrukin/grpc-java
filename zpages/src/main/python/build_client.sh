#!/bin/bash

GENERATED_OUT="../../generated/main/python/zpages"
mkdir -p $GENERATED_OUT
python -m grpc_tools.protoc -I../proto --python_out=$GENERATED_OUT --grpc_python_out=$GENERATED_OUT ../proto/empty_message.proto ../proto/expvar.proto ../proto/grpc_status.proto ../proto/message_set.proto ../proto/simple_service.proto
touch $GENERATED_OUT/__init__.py

