@echo off
rem check that JAVA_HOME and MCF_HOME are set
if not exist "%JAVA_HOME%\bin\java.exe" goto nojavahome
if not exist "%MCF_HOME%\properties.xml" goto nolcfhome
rem save existing path here
set OLDDIR=%CD%
cd "%MCF_HOME%\processes"
set CLASSPATH=.
for %%f in (jar/*) do call script\setclasspath.bat %%f
set JAVADEFINES=
for %%g in (define/*) do call script\setdefine.bat %%g
rem restore old path here
cd "%OLDDIR%"
"%JAVA_HOME%\bin\java" "-Dorg.apache.manifoldcf.configfile=%MCF_HOME%\properties.xml" %JAVADEFINES% -classpath "%CLASSPATH%" %*
goto done
:nojavahome
echo Environment variable JAVA_HOME is not set properly.
goto done
:nolcfhome
echo Environment variable MCF_HOME is not set properly.
goto done
:done
