# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import sys
import urllib
from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer

livelink_prefix = "/stuff/Livelink.exe"
server = None
alive = True

class LivelinkHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        # Verify both correct login command and correct subsequent requests,
        # as well as shutdown command.

        # Grab the path
        path = self.path
        # See if this is a 'command', or if it should be emulated a-la livelink
        if path.startswith(livelink_prefix):
            if len(path) > len(livelink_prefix):
                if path[len(livelink_prefix)] == '?':
                    command = path[len(livelink_prefix)+1:len(path)]
                    # Parse query string
                    args = command.split("&")
                    map = {}
                    for arg in args:
                        name,value = arg.split("=",1)
                        map[urllib.unquote(name)] = urllib.unquote(value)
                    if map.has_key("func"):
                        # Found an operation
                        if map["func"] == "ll.login":
                            # Found a login request
                            if not map.has_key("CurrentClientTime") or not map.has_key("NextURL") or not map.has_key("Username") or not map.has_key("Password"):
                                self.send_response(400)
                            else:
                                # Verify correctness of NextURL field
                                nexturl = map["NextURL"]
                                args = nexturl.split("&")
                                if not args[0].startswith("/stuff/Livelink.exe"):
                                    self.send_response(400)
                                else:
                                    # It all checks out
                                    self.send_response(200)
                                    self.send_header("Content-type","text/html")
                        else:
                            self.send_response(400)
                    else:
                        # Bad arguments
                        self.send_response(400)
                else:
                    # Bad arguments
                    self.send_response(400)
            else:
                # everything OK
                self.send_response(200)
                self.send_header("Content-type","text/html")
        else:
            # Anything else: Hard exit
            # This didn't work; apparently 2.6 only
            #global server
            #server.shutdown()
            global alive
            alive = False

    def do_POST(self):
        # Don't accept POSTs
        self.send_response(400)

def main():
    global server
    global alive
    try:
        server = HTTPServer(('',8002),LivelinkHandler)
        while alive:
            server.handle_request()
    finally:
        server.socket.close()

if __name__ == '__main__':
    main()
