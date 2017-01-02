from __future__ import print_function

import os
import sys
import grpc

sys.path.insert(0, os.path.realpath("../../generated/main/python"))

from zpages.simple_service_pb2_grpc import SimpleStub
from zpages.simple_service_pb2 import EchoRequest
from zpages.simple_service_pb2 import EchoResponse
from flask import Flask

app = Flask(__name__)

@app.route("/")
def demo():
  channel = grpc.insecure_channel('localhost:8123')
  stub = SimpleStub(channel)
  response = stub.Echo(EchoRequest(echo = "Hello World\n", repeat_echo = 5))
  return response.echo_response

if __name__ == "__main__":
  app.run()

