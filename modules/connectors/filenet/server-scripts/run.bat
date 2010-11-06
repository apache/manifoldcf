@echo off
rem check that JAVA_HOME and MCF_HOME are set
if not exist "%JAVA_HOME%\bin\java.exe" goto nojavahome
if not exist "%MCF_HOME%\properties.xml" goto nolcfhome
rem save existing path here
set OLDDIR=%CD%
cd "%MCF_HOME%\filenet-server-process"
set CLASSPATH=.
for %%f in (jar/*) do call script\setclasspath.bat %%f
rem restore old path here
cd "%OLDDIR%"
set WASP_STATEMENT=""
if defined WASP_HOME set WASP_STATEMENT="-Dwasp.location=%WASP_HOME%"
set LIB_STATEMENT=""
if defined JAVA_LIB_PATH set LIB_STATEMENT="-Djava.library.path=%JAVA_LIB_PATH%"
"%JAVA_HOME%\bin\java" -Xmx512m -Xms32m "%WASP_STATEMENT%" "%LIB_STATEMENT%" -classpath "%CLASSPATH%" org.apache.manifoldcf.crawler.server.filenet.Filenet
goto done
:nojavahome
echo Environment variable JAVA_HOME is not set properly.
goto done
:nolcfhome
echo Environment variable MCF_HOME is not set properly.
goto done
:done
