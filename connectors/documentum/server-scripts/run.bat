@echo off
rem check that JAVA_HOME and MCF_HOME are set
if not exist "%JAVA_HOME%\bin\java.exe" goto nojavahome
if not exist "%MCF_HOME%\properties.xml" goto nolcfhome
rem TODO: Check this on a windows system!
if not exist "%DOCUMENTUM%\dmcl.ini" goto nodctmhome
rem save existing path here
set OLDDIR=%CD%
cd "%MCF_HOME%\documentum-server-process"
set CLASSPATH=.
for %%f in (jar/*) do call script\setclasspath.bat %%f
rem restore old path here
cd "%OLDDIR%"
set LIB_STATEMENT=
if defined JAVA_LIB_PATH set LIB_STATEMENT="-Djava.library.path=%JAVA_LIB_PATH%"
"%JAVA_HOME%\bin\java" -Xmx512m -Xms32m %LIB_STATEMENT% -classpath "%CLASSPATH%" org.apache.manifoldcf.crawler.server.DCTM.DCTM
goto done
:nojavahome
echo Environment variable JAVA_HOME is not set properly.
goto done
:nolcfhome
echo Environment variable MCF_HOME is not set properly.
goto done
:nodctmhome
echo Environment variable DOCUMENTUM is not set properly.
goto done
:done
