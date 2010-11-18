#!/usr/bin/python

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

import re
import pg
import sys
import time
import DNSFakeoutHelpers
import ConnectorHelpers
import WebConnectorHelpers
import sqatools
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion


# Run a document status report to get the expire and refetch times
def run_document_status_report( username, password, connection_name, job_id, url_match ):
    """ Return tuples of (expiration time, refetch time) """
    results = ConnectorHelpers.run_document_status_ui( username, password, connection_name,
            [ job_id ], identifier_regexp=url_match )

    if len(results) != 1:
        raise Exception("Expecting to see a single row for identifier %s in document status report, saw %d" % (url_match,len(results)))

    result = results[0]
    scheduled_time = result[ "Scheduled" ]
    scheduled_action = result[ "Scheduled Action" ]
    if scheduled_action == "Expire":
        time_value = ConnectorHelpers.parse_date_time(scheduled_time)
        return ( time_value, -1 )
    elif scheduled_action == "Process":
        time_value = ConnectorHelpers.parse_date_time(scheduled_time)
        return ( -1, time_value )
    return ( -1, -1 )

def clear_robots_cache():
    """ Clean out robots cache. """
    ConnectorHelpers.shutdown_agents()

    # Clear out robots database table
    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:
        db.query( "DELETE FROM robotsdata" )
    finally:
        db.close()

    ConnectorHelpers.start_agents()

# Use a generated database so I can accurately judge the real performance.
seed_list =     ["http://server0.net/document_0_for_server_0.htm",
                "http://server1.net/document_0_for_server_1.htm",
                "http://server2.net/document_0_for_server_2.htm",
                "http://server3.net/document_0_for_server_3.htm",
                "http://server4.net/document_0_for_server_4.htm",
                "http://server5.net/document_0_for_server_5.htm",
                "http://server6.net/document_0_for_server_6.htm",
                "http://server7.net/document_0_for_server_7.htm",
                "http://server8.net/document_0_for_server_8.htm",
                "http://server9.net/document_0_for_server_9.htm",
                "http://server10.net/document_0_for_server_10.htm",
                "http://server11.net/document_0_for_server_11.htm",
                "http://server12.net/document_0_for_server_12.htm",
                "http://server13.net/document_0_for_server_13.htm",
                "http://server14.net/document_0_for_server_14.htm",
                "http://server15.net/document_0_for_server_15.htm",
                "http://server16.net/document_0_for_server_16.htm",
                "http://server17.net/document_0_for_server_17.htm",
                "http://server18.net/document_0_for_server_18.htm",
                "http://server19.net/document_0_for_server_19.htm",
                "http://server20.net/document_0_for_server_20.htm",
                "http://server21.net/document_0_for_server_21.htm",
                "http://server22.net/document_0_for_server_22.htm",
                "http://server23.net/document_0_for_server_23.htm",
                "http://server24.net/document_0_for_server_24.htm",
                "http://server25.net/document_0_for_server_25.htm",
                "http://server26.net/document_0_for_server_26.htm",
                "http://server27.net/document_0_for_server_27.htm",
                "http://server28.net/document_0_for_server_28.htm",
                "http://server29.net/document_0_for_server_29.htm",
                "http://server30.net/document_0_for_server_30.htm",
                "http://server31.net/document_0_for_server_31.htm",
                "http://server32.net/document_0_for_server_32.htm",
                "http://server33.net/document_0_for_server_33.htm",
                "http://server34.net/document_0_for_server_34.htm",
                "http://server35.net/document_0_for_server_35.htm",
                "http://server36.net/document_0_for_server_36.htm",
                "http://server37.net/document_0_for_server_37.htm",
                "http://server38.net/document_0_for_server_38.htm",
                "http://server39.net/document_0_for_server_39.htm",
                "http://server40.net/document_0_for_server_40.htm",
                "http://server41.net/document_0_for_server_41.htm",
                "http://server42.net/document_0_for_server_42.htm",
                "http://server43.net/document_0_for_server_43.htm",
                "http://server44.net/document_0_for_server_44.htm",
                "http://server45.net/document_0_for_server_45.htm",
                "http://server46.net/document_0_for_server_46.htm",
                "http://server47.net/document_0_for_server_47.htm",
                "http://server48.net/document_0_for_server_48.htm",
                "http://server49.net/document_0_for_server_49.htm",
                "http://server50.net/document_0_for_server_50.htm",
                "http://server51.net/document_0_for_server_51.htm",
                "http://server52.net/document_0_for_server_52.htm",
                "http://server53.net/document_0_for_server_53.htm",
                "http://server54.net/document_0_for_server_54.htm",
                "http://server55.net/document_0_for_server_55.htm",
                "http://server56.net/document_0_for_server_56.htm",
                "http://server57.net/document_0_for_server_57.htm",
                "http://server58.net/document_0_for_server_58.htm",
                "http://server59.net/document_0_for_server_59.htm",
                "http://server60.net/document_0_for_server_60.htm",
                "http://server61.net/document_0_for_server_61.htm",
                "http://server62.net/document_0_for_server_62.htm",
                "http://server63.net/document_0_for_server_63.htm",
                "http://server64.net/document_0_for_server_64.htm",
                "http://server65.net/document_0_for_server_65.htm",
                "http://server66.net/document_0_for_server_66.htm",
                "http://server67.net/document_0_for_server_67.htm",
                "http://server68.net/document_0_for_server_68.htm",
                "http://server69.net/document_0_for_server_69.htm",
                "http://server70.net/document_0_for_server_70.htm",
                "http://server71.net/document_0_for_server_71.htm",
                "http://server72.net/document_0_for_server_72.htm",
                "http://server73.net/document_0_for_server_73.htm",
                "http://server74.net/document_0_for_server_74.htm",
                "http://server75.net/document_0_for_server_75.htm",
                "http://server76.net/document_0_for_server_76.htm",
                "http://server77.net/document_0_for_server_77.htm",
                "http://server78.net/document_0_for_server_78.htm",
                "http://server79.net/document_0_for_server_79.htm",
                "http://server80.net/document_0_for_server_80.htm",
                "http://server81.net/document_0_for_server_81.htm",
                "http://server82.net/document_0_for_server_82.htm",
                "http://server83.net/document_0_for_server_83.htm",
                "http://server84.net/document_0_for_server_84.htm",
                "http://server85.net/document_0_for_server_85.htm",
                "http://server86.net/document_0_for_server_86.htm",
                "http://server87.net/document_0_for_server_87.htm",
                "http://server88.net/document_0_for_server_88.htm",
                "http://server89.net/document_0_for_server_89.htm",
                "http://server90.net/document_0_for_server_90.htm",
                "http://server91.net/document_0_for_server_91.htm",
                "http://server92.net/document_0_for_server_92.htm",
                "http://server93.net/document_0_for_server_93.htm",
                "http://server94.net/document_0_for_server_94.htm",
                "http://server95.net/document_0_for_server_95.htm",
                "http://server96.net/document_0_for_server_96.htm",
                "http://server97.net/document_0_for_server_97.htm",
                "http://server98.net/document_0_for_server_98.htm",
                "http://server99.net/document_0_for_server_99.htm",
                "http://server100.net/document_0_for_server_100.htm",
                "http://server101.net/document_0_for_server_101.htm",
                "http://server102.net/document_0_for_server_102.htm",
                "http://server103.net/document_0_for_server_103.htm",
                "http://server104.net/document_0_for_server_104.htm",
                "http://server105.net/document_0_for_server_105.htm",
                "http://server106.net/document_0_for_server_106.htm",
                "http://server107.net/document_0_for_server_107.htm",
                "http://server108.net/document_0_for_server_108.htm",
                "http://server109.net/document_0_for_server_109.htm",
                "http://server110.net/document_0_for_server_110.htm",
                "http://server111.net/document_0_for_server_111.htm",
                "http://server112.net/document_0_for_server_112.htm",
                "http://server113.net/document_0_for_server_113.htm",
                "http://server114.net/document_0_for_server_114.htm",
                "http://server115.net/document_0_for_server_115.htm",
                "http://server116.net/document_0_for_server_116.htm",
                "http://server117.net/document_0_for_server_117.htm",
                "http://server118.net/document_0_for_server_118.htm",
                "http://server119.net/document_0_for_server_119.htm",
                "http://server120.net/document_0_for_server_120.htm",
                "http://server121.net/document_0_for_server_121.htm",
                "http://server122.net/document_0_for_server_122.htm",
                "http://server123.net/document_0_for_server_123.htm",
                "http://server124.net/document_0_for_server_124.htm",
                "http://server125.net/document_0_for_server_125.htm",
                "http://server126.net/document_0_for_server_126.htm",
                "http://server127.net/document_0_for_server_127.htm",
                "http://server128.net/document_0_for_server_128.htm",
                "http://server129.net/document_0_for_server_129.htm",
                "http://server130.net/document_0_for_server_130.htm",
                "http://server131.net/document_0_for_server_131.htm",
                "http://server132.net/document_0_for_server_132.htm",
                "http://server133.net/document_0_for_server_133.htm",
                "http://server134.net/document_0_for_server_134.htm",
                "http://server135.net/document_0_for_server_135.htm",
                "http://server136.net/document_0_for_server_136.htm",
                "http://server137.net/document_0_for_server_137.htm",
                "http://server138.net/document_0_for_server_138.htm",
                "http://server139.net/document_0_for_server_139.htm",
                "http://server140.net/document_0_for_server_140.htm",
                "http://server141.net/document_0_for_server_141.htm",
                "http://server142.net/document_0_for_server_142.htm",
                "http://server143.net/document_0_for_server_143.htm",
                "http://server144.net/document_0_for_server_144.htm",
                "http://server145.net/document_0_for_server_145.htm",
                "http://server146.net/document_0_for_server_146.htm",
                "http://server147.net/document_0_for_server_147.htm",
                "http://server148.net/document_0_for_server_148.htm",
                "http://server149.net/document_0_for_server_149.htm",
                "http://server150.net/document_0_for_server_150.htm",
                "http://server151.net/document_0_for_server_151.htm",
                "http://server152.net/document_0_for_server_152.htm",
                "http://server153.net/document_0_for_server_153.htm",
                "http://server154.net/document_0_for_server_154.htm",
                "http://server155.net/document_0_for_server_155.htm",
                "http://server156.net/document_0_for_server_156.htm",
                "http://server157.net/document_0_for_server_157.htm",
                "http://server158.net/document_0_for_server_158.htm",
                "http://server159.net/document_0_for_server_159.htm",
                "http://server160.net/document_0_for_server_160.htm",
                "http://server161.net/document_0_for_server_161.htm",
                "http://server162.net/document_0_for_server_162.htm",
                "http://server163.net/document_0_for_server_163.htm",
                "http://server164.net/document_0_for_server_164.htm",
                "http://server165.net/document_0_for_server_165.htm",
                "http://server166.net/document_0_for_server_166.htm",
                "http://server167.net/document_0_for_server_167.htm",
                "http://server168.net/document_0_for_server_168.htm",
                "http://server169.net/document_0_for_server_169.htm",
                "http://server170.net/document_0_for_server_170.htm",
                "http://server171.net/document_0_for_server_171.htm",
                "http://server172.net/document_0_for_server_172.htm",
                "http://server173.net/document_0_for_server_173.htm",
                "http://server174.net/document_0_for_server_174.htm",
                "http://server175.net/document_0_for_server_175.htm",
                "http://server176.net/document_0_for_server_176.htm",
                "http://server177.net/document_0_for_server_177.htm",
                "http://server178.net/document_0_for_server_178.htm",
                "http://server179.net/document_0_for_server_179.htm",
                "http://server180.net/document_0_for_server_180.htm",
                "http://server181.net/document_0_for_server_181.htm",
                "http://server182.net/document_0_for_server_182.htm",
                "http://server183.net/document_0_for_server_183.htm",
                "http://server184.net/document_0_for_server_184.htm",
                "http://server185.net/document_0_for_server_185.htm",
                "http://server186.net/document_0_for_server_186.htm",
                "http://server187.net/document_0_for_server_187.htm",
                "http://server188.net/document_0_for_server_188.htm",
                "http://server189.net/document_0_for_server_189.htm",
                "http://server190.net/document_0_for_server_190.htm",
                "http://server191.net/document_0_for_server_191.htm",
                "http://server192.net/document_0_for_server_192.htm",
                "http://server193.net/document_0_for_server_193.htm",
                "http://server194.net/document_0_for_server_194.htm",
                "http://server195.net/document_0_for_server_195.htm",
                "http://server196.net/document_0_for_server_196.htm",
                "http://server197.net/document_0_for_server_197.htm",
                "http://server198.net/document_0_for_server_198.htm",
                "http://server199.net/document_0_for_server_199.htm",
                "http://server200.net/document_0_for_server_200.htm",
                "http://server201.net/document_0_for_server_201.htm",
                "http://server202.net/document_0_for_server_202.htm",
                "http://server203.net/document_0_for_server_203.htm",
                "http://server204.net/document_0_for_server_204.htm",
                "http://server205.net/document_0_for_server_205.htm",
                "http://server206.net/document_0_for_server_206.htm",
                "http://server207.net/document_0_for_server_207.htm",
                "http://server208.net/document_0_for_server_208.htm",
                "http://server209.net/document_0_for_server_209.htm",
                "http://server210.net/document_0_for_server_210.htm",
                "http://server211.net/document_0_for_server_211.htm",
                "http://server212.net/document_0_for_server_212.htm",
                "http://server213.net/document_0_for_server_213.htm",
                "http://server214.net/document_0_for_server_214.htm",
                "http://server215.net/document_0_for_server_215.htm",
                "http://server216.net/document_0_for_server_216.htm",
                "http://server217.net/document_0_for_server_217.htm",
                "http://server218.net/document_0_for_server_218.htm",
                "http://server219.net/document_0_for_server_219.htm",
                "http://server220.net/document_0_for_server_220.htm",
                "http://server221.net/document_0_for_server_221.htm",
                "http://server222.net/document_0_for_server_222.htm",
                "http://server223.net/document_0_for_server_223.htm",
                "http://server224.net/document_0_for_server_224.htm",
                "http://server225.net/document_0_for_server_225.htm",
                "http://server226.net/document_0_for_server_226.htm",
                "http://server227.net/document_0_for_server_227.htm",
                "http://server228.net/document_0_for_server_228.htm",
                "http://server229.net/document_0_for_server_229.htm",
                "http://server230.net/document_0_for_server_230.htm",
                "http://server231.net/document_0_for_server_231.htm",
                "http://server232.net/document_0_for_server_232.htm",
                "http://server233.net/document_0_for_server_233.htm",
                "http://server234.net/document_0_for_server_234.htm",
                "http://server235.net/document_0_for_server_235.htm",
                "http://server236.net/document_0_for_server_236.htm",
                "http://server237.net/document_0_for_server_237.htm",
                "http://server238.net/document_0_for_server_238.htm",
                "http://server239.net/document_0_for_server_239.htm",
                "http://server240.net/document_0_for_server_240.htm",
                "http://server241.net/document_0_for_server_241.htm",
                "http://server242.net/document_0_for_server_242.htm",
                "http://server243.net/document_0_for_server_243.htm",
                "http://server244.net/document_0_for_server_244.htm",
                "http://server245.net/document_0_for_server_245.htm",
                "http://server246.net/document_0_for_server_246.htm",
                "http://server247.net/document_0_for_server_247.htm",
                "http://server248.net/document_0_for_server_248.htm",
                "http://server249.net/document_0_for_server_249.htm",
                "http://server250.net/document_0_for_server_250.htm",
                "http://server251.net/document_0_for_server_251.htm",
                "http://server252.net/document_0_for_server_252.htm",
                "http://server253.net/document_0_for_server_253.htm",
                "http://server254.net/document_0_for_server_254.htm",
                "http://server255.net/document_0_for_server_255.htm",
                "http://server256.net/document_0_for_server_256.htm",
                "http://server257.net/document_0_for_server_257.htm",
                "http://server258.net/document_0_for_server_258.htm",
                "http://server259.net/document_0_for_server_259.htm",
                "http://server260.net/document_0_for_server_260.htm",
                "http://server261.net/document_0_for_server_261.htm",
                "http://server262.net/document_0_for_server_262.htm",
                "http://server263.net/document_0_for_server_263.htm",
                "http://server264.net/document_0_for_server_264.htm",
                "http://server265.net/document_0_for_server_265.htm",
                "http://server266.net/document_0_for_server_266.htm",
                "http://server267.net/document_0_for_server_267.htm",
                "http://server268.net/document_0_for_server_268.htm",
                "http://server269.net/document_0_for_server_269.htm",
                "http://server270.net/document_0_for_server_270.htm",
                "http://server271.net/document_0_for_server_271.htm",
                "http://server272.net/document_0_for_server_272.htm",
                "http://server273.net/document_0_for_server_273.htm",
                "http://server274.net/document_0_for_server_274.htm",
                "http://server275.net/document_0_for_server_275.htm",
                "http://server276.net/document_0_for_server_276.htm",
                "http://server277.net/document_0_for_server_277.htm",
                "http://server278.net/document_0_for_server_278.htm",
                "http://server279.net/document_0_for_server_279.htm",
                "http://server280.net/document_0_for_server_280.htm",
                "http://server281.net/document_0_for_server_281.htm",
                "http://server282.net/document_0_for_server_282.htm",
                "http://server283.net/document_0_for_server_283.htm",
                "http://server284.net/document_0_for_server_284.htm",
                "http://server285.net/document_0_for_server_285.htm",
                "http://server286.net/document_0_for_server_286.htm",
                "http://server287.net/document_0_for_server_287.htm",
                "http://server288.net/document_0_for_server_288.htm",
                "http://server289.net/document_0_for_server_289.htm",
                "http://server290.net/document_0_for_server_290.htm",
                "http://server291.net/document_0_for_server_291.htm",
                "http://server292.net/document_0_for_server_292.htm",
                "http://server293.net/document_0_for_server_293.htm",
                "http://server294.net/document_0_for_server_294.htm",
                "http://server295.net/document_0_for_server_295.htm",
                "http://server296.net/document_0_for_server_296.htm",
                "http://server297.net/document_0_for_server_297.htm",
                "http://server298.net/document_0_for_server_298.htm",
                "http://server299.net/document_0_for_server_299.htm",
                "http://server300.net/document_0_for_server_300.htm",
                "http://server301.net/document_0_for_server_301.htm",
                "http://server302.net/document_0_for_server_302.htm",
                "http://server303.net/document_0_for_server_303.htm",
                "http://server304.net/document_0_for_server_304.htm",
                "http://server305.net/document_0_for_server_305.htm",
                "http://server306.net/document_0_for_server_306.htm",
                "http://server307.net/document_0_for_server_307.htm",
                "http://server308.net/document_0_for_server_308.htm",
                "http://server309.net/document_0_for_server_309.htm",
                "http://server310.net/document_0_for_server_310.htm",
                "http://server311.net/document_0_for_server_311.htm",
                "http://server312.net/document_0_for_server_312.htm",
                "http://server313.net/document_0_for_server_313.htm",
                "http://server314.net/document_0_for_server_314.htm",
                "http://server315.net/document_0_for_server_315.htm",
                "http://server316.net/document_0_for_server_316.htm",
                "http://server317.net/document_0_for_server_317.htm",
                "http://server318.net/document_0_for_server_318.htm",
                "http://server319.net/document_0_for_server_319.htm",
                "http://server320.net/document_0_for_server_320.htm",
                "http://server321.net/document_0_for_server_321.htm",
                "http://server322.net/document_0_for_server_322.htm",
                "http://server323.net/document_0_for_server_323.htm",
                "http://server324.net/document_0_for_server_324.htm",
                "http://server325.net/document_0_for_server_325.htm",
                "http://server326.net/document_0_for_server_326.htm",
                "http://server327.net/document_0_for_server_327.htm",
                "http://server328.net/document_0_for_server_328.htm",
                "http://server329.net/document_0_for_server_329.htm",
                "http://server330.net/document_0_for_server_330.htm",
                "http://server331.net/document_0_for_server_331.htm",
                "http://server332.net/document_0_for_server_332.htm",
                "http://server333.net/document_0_for_server_333.htm",
                "http://server334.net/document_0_for_server_334.htm",
                "http://server335.net/document_0_for_server_335.htm",
                "http://server336.net/document_0_for_server_336.htm",
                "http://server337.net/document_0_for_server_337.htm",
                "http://server338.net/document_0_for_server_338.htm",
                "http://server339.net/document_0_for_server_339.htm",
                "http://server340.net/document_0_for_server_340.htm",
                "http://server341.net/document_0_for_server_341.htm",
                "http://server342.net/document_0_for_server_342.htm",
                "http://server343.net/document_0_for_server_343.htm",
                "http://server344.net/document_0_for_server_344.htm",
                "http://server345.net/document_0_for_server_345.htm",
                "http://server346.net/document_0_for_server_346.htm",
                "http://server347.net/document_0_for_server_347.htm",
                "http://server348.net/document_0_for_server_348.htm",
                "http://server349.net/document_0_for_server_349.htm",
                "http://server350.net/document_0_for_server_350.htm",
                "http://server351.net/document_0_for_server_351.htm",
                "http://server352.net/document_0_for_server_352.htm",
                "http://server353.net/document_0_for_server_353.htm",
                "http://server354.net/document_0_for_server_354.htm",
                "http://server355.net/document_0_for_server_355.htm",
                "http://server356.net/document_0_for_server_356.htm",
                "http://server357.net/document_0_for_server_357.htm",
                "http://server358.net/document_0_for_server_358.htm",
                "http://server359.net/document_0_for_server_359.htm",
                "http://server360.net/document_0_for_server_360.htm",
                "http://server361.net/document_0_for_server_361.htm",
                "http://server362.net/document_0_for_server_362.htm",
                "http://server363.net/document_0_for_server_363.htm",
                "http://server364.net/document_0_for_server_364.htm",
                "http://server365.net/document_0_for_server_365.htm",
                "http://server366.net/document_0_for_server_366.htm",
                "http://server367.net/document_0_for_server_367.htm",
                "http://server368.net/document_0_for_server_368.htm",
                "http://server369.net/document_0_for_server_369.htm",
                "http://server370.net/document_0_for_server_370.htm",
                "http://server371.net/document_0_for_server_371.htm",
                "http://server372.net/document_0_for_server_372.htm",
                "http://server373.net/document_0_for_server_373.htm",
                "http://server374.net/document_0_for_server_374.htm",
                "http://server375.net/document_0_for_server_375.htm",
                "http://server376.net/document_0_for_server_376.htm",
                "http://server377.net/document_0_for_server_377.htm",
                "http://server378.net/document_0_for_server_378.htm",
                "http://server379.net/document_0_for_server_379.htm",
                "http://server380.net/document_0_for_server_380.htm",
                "http://server381.net/document_0_for_server_381.htm",
                "http://server382.net/document_0_for_server_382.htm",
                "http://server383.net/document_0_for_server_383.htm",
                "http://server384.net/document_0_for_server_384.htm",
                "http://server385.net/document_0_for_server_385.htm",
                "http://server386.net/document_0_for_server_386.htm",
                "http://server387.net/document_0_for_server_387.htm",
                "http://server388.net/document_0_for_server_388.htm",
                "http://server389.net/document_0_for_server_389.htm",
                "http://server390.net/document_0_for_server_390.htm",
                "http://server391.net/document_0_for_server_391.htm",
                "http://server392.net/document_0_for_server_392.htm",
                "http://server393.net/document_0_for_server_393.htm",
                "http://server394.net/document_0_for_server_394.htm",
                "http://server395.net/document_0_for_server_395.htm",
                "http://server396.net/document_0_for_server_396.htm",
                "http://server397.net/document_0_for_server_397.htm",
                "http://server398.net/document_0_for_server_398.htm",
                "http://server399.net/document_0_for_server_399.htm",
                "http://server400.net/document_0_for_server_400.htm",
                "http://server401.net/document_0_for_server_401.htm",
                "http://server402.net/document_0_for_server_402.htm",
                "http://server403.net/document_0_for_server_403.htm",
                "http://server404.net/document_0_for_server_404.htm",
                "http://server405.net/document_0_for_server_405.htm",
                "http://server406.net/document_0_for_server_406.htm",
                "http://server407.net/document_0_for_server_407.htm",
                "http://server408.net/document_0_for_server_408.htm",
                "http://server409.net/document_0_for_server_409.htm",
                "http://server410.net/document_0_for_server_410.htm",
                "http://server411.net/document_0_for_server_411.htm",
                "http://server412.net/document_0_for_server_412.htm",
                "http://server413.net/document_0_for_server_413.htm",
                "http://server414.net/document_0_for_server_414.htm",
                "http://server415.net/document_0_for_server_415.htm",
                "http://server416.net/document_0_for_server_416.htm",
                "http://server417.net/document_0_for_server_417.htm",
                "http://server418.net/document_0_for_server_418.htm",
                "http://server419.net/document_0_for_server_419.htm",
                "http://server420.net/document_0_for_server_420.htm",
                "http://server421.net/document_0_for_server_421.htm",
                "http://server422.net/document_0_for_server_422.htm",
                "http://server423.net/document_0_for_server_423.htm",
                "http://server424.net/document_0_for_server_424.htm",
                "http://server425.net/document_0_for_server_425.htm",
                "http://server426.net/document_0_for_server_426.htm",
                "http://server427.net/document_0_for_server_427.htm",
                "http://server428.net/document_0_for_server_428.htm",
                "http://server429.net/document_0_for_server_429.htm",
                "http://server430.net/document_0_for_server_430.htm",
                "http://server431.net/document_0_for_server_431.htm",
                "http://server432.net/document_0_for_server_432.htm",
                "http://server433.net/document_0_for_server_433.htm",
                "http://server434.net/document_0_for_server_434.htm",
                "http://server435.net/document_0_for_server_435.htm",
                "http://server436.net/document_0_for_server_436.htm",
                "http://server437.net/document_0_for_server_437.htm",
                "http://server438.net/document_0_for_server_438.htm",
                "http://server439.net/document_0_for_server_439.htm",
                "http://server440.net/document_0_for_server_440.htm",
                "http://server441.net/document_0_for_server_441.htm",
                "http://server442.net/document_0_for_server_442.htm",
                "http://server443.net/document_0_for_server_443.htm",
                "http://server444.net/document_0_for_server_444.htm",
                "http://server445.net/document_0_for_server_445.htm",
                "http://server446.net/document_0_for_server_446.htm",
                "http://server447.net/document_0_for_server_447.htm",
                "http://server448.net/document_0_for_server_448.htm",
                "http://server449.net/document_0_for_server_449.htm",
                "http://server450.net/document_0_for_server_450.htm",
                "http://server451.net/document_0_for_server_451.htm",
                "http://server452.net/document_0_for_server_452.htm",
                "http://server453.net/document_0_for_server_453.htm",
                "http://server454.net/document_0_for_server_454.htm",
                "http://server455.net/document_0_for_server_455.htm",
                "http://server456.net/document_0_for_server_456.htm",
                "http://server457.net/document_0_for_server_457.htm",
                "http://server458.net/document_0_for_server_458.htm",
                "http://server459.net/document_0_for_server_459.htm",
                "http://server460.net/document_0_for_server_460.htm",
                "http://server461.net/document_0_for_server_461.htm",
                "http://server462.net/document_0_for_server_462.htm",
                "http://server463.net/document_0_for_server_463.htm",
                "http://server464.net/document_0_for_server_464.htm",
                "http://server465.net/document_0_for_server_465.htm",
                "http://server466.net/document_0_for_server_466.htm",
                "http://server467.net/document_0_for_server_467.htm",
                "http://server468.net/document_0_for_server_468.htm",
                "http://server469.net/document_0_for_server_469.htm",
                "http://server470.net/document_0_for_server_470.htm",
                "http://server471.net/document_0_for_server_471.htm",
                "http://server472.net/document_0_for_server_472.htm",
                "http://server473.net/document_0_for_server_473.htm",
                "http://server474.net/document_0_for_server_474.htm",
                "http://server475.net/document_0_for_server_475.htm",
                "http://server476.net/document_0_for_server_476.htm",
                "http://server477.net/document_0_for_server_477.htm",
                "http://server478.net/document_0_for_server_478.htm",
                "http://server479.net/document_0_for_server_479.htm",
                "http://server480.net/document_0_for_server_480.htm",
                "http://server481.net/document_0_for_server_481.htm",
                "http://server482.net/document_0_for_server_482.htm",
                "http://server483.net/document_0_for_server_483.htm",
                "http://server484.net/document_0_for_server_484.htm",
                "http://server485.net/document_0_for_server_485.htm",
                "http://server486.net/document_0_for_server_486.htm",
                "http://server487.net/document_0_for_server_487.htm",
                "http://server488.net/document_0_for_server_488.htm",
                "http://server489.net/document_0_for_server_489.htm",
                "http://server490.net/document_0_for_server_490.htm",
                "http://server491.net/document_0_for_server_491.htm",
                "http://server492.net/document_0_for_server_492.htm",
                "http://server493.net/document_0_for_server_493.htm",
                "http://server494.net/document_0_for_server_494.htm",
                "http://server495.net/document_0_for_server_495.htm",
                "http://server496.net/document_0_for_server_496.htm",
                "http://server497.net/document_0_for_server_497.htm",
                "http://server498.net/document_0_for_server_498.htm",
                "http://server499.net/document_0_for_server_499.htm",
                "http://server500.net/document_0_for_server_500.htm",
                "http://server501.net/document_0_for_server_501.htm",
                "http://server502.net/document_0_for_server_502.htm",
                "http://server503.net/document_0_for_server_503.htm",
                "http://server504.net/document_0_for_server_504.htm",
                "http://server505.net/document_0_for_server_505.htm",
                "http://server506.net/document_0_for_server_506.htm",
                "http://server507.net/document_0_for_server_507.htm",
                "http://server508.net/document_0_for_server_508.htm",
                "http://server509.net/document_0_for_server_509.htm" ]

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( target_server, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # End all jobs and clean up before undoing redirection
    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e


    # End redirection first, then clean up session.  This permits us to clean up the
    # session on the correct websimulator machine.
    try:
        DNSFakeoutHelpers.end_dns_redirection()
    except Exception, e:
        if print_errors:
            print "Error ending dns redirection"
            print e

    # End the current session - but specifically tell it to end the session on
    # the websimulator machine we intend to use.
    try:
        # End the current session
        DNSFakeoutHelpers.end_session_remote(server_name=target_server)
    except Exception, e:
        if print_errors:
            print "Error ending dns capture session"
            print e

    try:
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error deleting crawl user"
            print e

    try:
        LicenseMakerClient.revoke_license()
    except Exception, e:
        if print_errors:
            print "Error cleaning up old license"
            print e

    try:
        ConnectorHelpers.teardown_connector_environment( )
    except Exception, e:
        if print_errors:
            print "Error cleaning up debs"
            print e


# Main
if __name__ == '__main__':

    if len(sys.argv) > 1:
        target_machine = sys.argv[1]
        if len(sys.argv) > 2:
            internal_port = int(sys.argv[2])
        else:
            internal_port = 53
    else:
        target_machine = "duck60.metacarta.com"
        internal_port = 53

    print "Precleaning!"

    preclean( target_machine, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["webConnector"], detect_gdms=True)

    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Now, perform the dns redirection.  This will mean that all dns requests to anything other than localhost will go somewhere
    # else.
    DNSFakeoutHelpers.initialize_dns_redirection(target_machine,internal_port)

    # Create a session
    session_id = "session_%f" % time.time()
    DNSFakeoutHelpers.begin_session_remote(session_id,"generated_web_db")

    print "Running postgresql maintenance (to make timings consistent)"

    ConnectorHelpers.run_maintenance()

    print "Clearing out robots cache"

    clear_robots_cache()

    print "Set up web connection."

    # Define repository connection.  The right way to do it is to set the average rate for what we want, and the maximum rate should set a hard limit
    # to what's acceptable, usually higher than the average rate.  This is reflected in the settings below.
    WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com",
                                        max_repository_connections=100,
                                        throttles=[("","All individual domains",str(24))],
                                        limits=[ { "regexp":"", "connections":2, "kbpersecond":64, "fetchesperminute":30 } ] )

    job_id = WebConnectorHelpers.define_web_job_ui( username,
                        password,
                        "WEBJob",
                        "WEBConnection",
                        seed_list )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Ok, run the report and grab the analysis we want
    # The best fetch rate is 24 docs per minute, but we have to adjust also for the fetch delay.  The average document size is about 75K, so that fetch delay
    # will account for 1.2 seconds per document.  If my calculations are right, that yields 16.2 docs per minute when all is said and done.
    analysis = DNSFakeoutHelpers.run_session_report_remote( "latency_report.py", [str(16)] )

    # Assess the maximum difference from ideal for all the rows returned in this report.  If we get something back that looks like it isn't in the format we expect,
    # it probably means there was an error string returned instead, so just print that.
    max_delay = 0
    max_url = "None"
    lines = analysis.splitlines()
    for line in lines:
        # Line is in format:
        # doc_count,url,first_fetch,last_fetch,actual_fetch_duration,overall_fetch_duration,actual_fetch_rate,overall_fetch_rate,actual_difference_from_ideal
        # First fetch and Last fetch are times; everything else is a number, except time deltas.  For example:
        # 5       www.kitsapsun.com                       2008-10-16 10:12:46.718005    2008-10-16 10:17:09.371442      0:04:22.653437  0:06:09.371442  1.15    0.81 0:04:10.153437
        if line != "":
            fields = line.split("\t")
            if len(fields) != 10:
                raise Exception("Report response in unexpected form: %s" % line)
            server_name = fields[1].strip()
            diff = fields[9].strip()
            if diff != "None":
                # Convert the timedelta to some number of seconds
                d = re.match(
                    r'((?P<days>\d+) days, )?(?P<hours>\d+):'
                    r'(?P<minutes>\d+):(?P<seconds>\d+)',
                    diff).groupdict(0)
                delta = int(d["seconds"]) + int(d["minutes"]) * 60 + int(d["hours"]) * 3600 + int(d["days"]) * 86400

                if delta > max_delay:
                    max_delay = delta
                    max_url = server_name

    # Since this is a generated feed, I can be certain there are no overlaps, so a pretty tight window is a reasonable thing to do.
    if max_delay > 15 * 60:
        raise Exception("Report response indicates that %s has too large a latency: %d minutes" % (max_url,max_delay))

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )

    # End the current session
    DNSFakeoutHelpers.end_session_remote()

    # Stop the redirection
    DNSFakeoutHelpers.end_dns_redirection()

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license()

    ConnectorHelpers.teardown_connector_environment( )

    print "Performance WEBConnector test PASSED"
