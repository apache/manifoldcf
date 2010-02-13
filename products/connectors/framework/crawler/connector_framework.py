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

# IF YOU ARE READING THIS, YOU ARE VIOLATING YOUR LICENSE AGREEMENT.

"""
metacarta_pgsql_dump plugin for the Connector Framework
"""

__revision__ = "$Id$"
__version__ = "%s/%s" % (__revision__.split()[3], __revision__.split()[2])

import os
import subprocess
import sys
import pg
sys.path.append( '/usr/lib/metacarta' )
import PgsqlCluster

DatabaseName = 'metacarta'
DatabaseUserName = 'metacarta'
DatabasePassword = 'atracatem'
ConfigTableList = \
    ['jobcollections', 'jobhopfilters', 'schedules', 'throttlespec', \
     'authconnections', 'jobs', 'repoconnections']

def ReversedTableList( tables ):
    reversedTables = []
    order = range( 0, len(ConfigTableList) )
    order.reverse()
    for idx in order:
        reversedTables.append( ConfigTableList[idx] )
    return reversedTables

def CallProg( prog, env = {} ):
    retcode = 0
    try:
        retcode = subprocess.call( args = prog, env=env )
        if retcode < 0:
            raise Exception( '(terminated by signal %s)' % -retcode )
        elif retcode > 0:
            raise Exception( '(returned %s)' % retcode )
    except Exception, e:
        print 'Caught exception %s executing %s' % (str(e), prog[0])
        if not retcode:
            retcode = -1
        return retcode
    return retcode

def CleanDumpDirectory():
    print 'Cleaning PostgreSQL dump directory %s' % PgsqlCluster.PgsqlDatabaseCluster.DumpDirectory
    for table in ConfigTableList:
        outputFile = \
            os.path.join( PgsqlCluster.PgsqlDatabaseCluster.DumpDirectory, '%s.sql' % table )
        if os.path.exists( outputFile ):
            try:
                os.remove( outputFile )
            except Exception, e:
                print 'Unable to remove existing dump file %s (%s)' % \
                    (outputFile, str(e))
                return False

def name():
    return 'connector-framework'

def description():
    return 'Connector Framework Add-on'

def priority():
    return 0

def dump():
    if not preDump():
        return False
    CleanDumpDirectory()
    returnCode = True
    for table in ConfigTableList:
        print 'Dumping Connector Framework database table %s.%s' % \
            (DatabaseName, table)
        outputFile = \
            os.path.join( PgsqlCluster.PgsqlDatabaseCluster.DumpDirectory, '%s.sql' % table )
        rc = CallProg( ['/usr/bin/pg_dump', '--cluster', '8.3/agents', '-d', DatabaseName, \
            '-f', outputFile, '-t', table, '-U', DatabaseUserName, \
            '--format=c', '--disable-triggers'], env = { 'PGPASSWORD': DatabasePassword } )
        if rc:
            returnCode = False
            break
    if returnCode:
        returnCode = postDump()
    return returnCode

def preDump():
    return True

def postDump():
    return True

def restore():
    """Do the restore with the tables in reverse order to ensure referential
    integrity."""
    if not preRestore():
        return False

    returnCode = True
    restoreOrder = range( 0, len(ConfigTableList) )
    restoreOrder.reverse()
    for table in ReversedTableList(ConfigTableList):
        print 'Restoring Connector Framework database table %s.%s' % \
            (DatabaseName, table)
        inputFile = \
            os.path.join( PgsqlCluster.PgsqlDatabaseCluster.RestoreDirectory, '%s.sql' % table )
        if not os.path.exists( inputFile ):
            raise Exception( 'Dump file for table %s.%s does not exist.' % \
                (DatabaseName, table) )
        rc = CallProg( ['sudo', '-u', 'postgres', '/usr/bin/pg_restore', '--cluster', '8.3/agents', '-d', DatabaseName, '-a', \
                        '--disable-triggers', '-U', 'postgres', inputFile] )
        if rc:
            returnCode = False
            break
    if returnCode:
        returnCode = postRestore()
    return returnCode

def preRestore():
    """Stop metacarta-agents otherwise database integrity performing these
    restore operations cannot be ensured.  If this is a backend server, then
    metacarta-agents should already be stopped."""

    rc = CallProg( ['/etc/init.d/metacarta-agents', 'stop'] )
    if rc:
        return False

    db = None
    try:
        db = pg.DB( DatabaseName, '127.0.0.1', 5432, None, None, \
                    DatabaseUserName, DatabasePassword )
        db.query( 'START TRANSACTION' )
        for table in ConfigTableList:
            db.query( 'DELETE FROM %s' % table )
        db.query( 'COMMIT' )
        db.close()
        db = None
    except Exception, e:
        print 'Failed to clean Connector Framework tables (%s).' % \
            str(e)
        if db:
            db.query( 'ROLLBACK' )
            db.close()
        return False
    return True

def postRestore():
    """Start services?
    Special post-restore processing of the jobs table."""

    db = None
    try:
        db = pg.DB( DatabaseName, '127.0.0.1', 5432, None, None, \
                    DatabaseUserName, DatabasePassword )
        table = 'jobs'
        if "public.%s" % (table) not in db.get_tables():
            raise Exception( 'The jobs table does not exist in database %s' % \
                             DatabaseName )
        nullCols = ['reseedtime', 'starttime', 'lastchecktime', 'endtime', \
                    'errortext', 'windowend']
        db.query( 'START TRANSACTION' )
        for col in nullCols:
            db.query( 'UPDATE %s SET %s=NULL' % (table, col) )
        db.query( 'UPDATE %s SET lasttime=0' % table )
        db.query( 'UPDATE %s SET status=\'N\'' % table )
        db.query( 'COMMIT' )
        db.close()
        db = None
    except Exception, e:
        print 'Failed to perform update %s Connector Framework jobs table.' % \
            str(e)
        if db:
            db.query( 'ROLLBACK' )
            db.close()
        return False
    return True

if __name__ == '__main__':
    if len( sys.argv ) > 1:
        funcName = sys.argv[1]
        if funcName not in globals().keys() or \
              not callable(globals()[funcName]):
            sys.exit( '%s is not a valid plugin function' % funcName )
        func = globals()[funcName]
        print func()
