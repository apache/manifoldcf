@echo off
rem check that JAVA_HOME and MCF_HOME are set
if not exist "%JAVA_HOME%\bin\java.exe" goto nojavahome
if not exist "%MCF_HOME%\properties.xml" goto nolcfhome
rem save existing path here
set OLDDIR=%CD%
cd "%MCF_HOME%\filenet-registry-process"
set CLASSPATH=.
for %%f in (jar/*) do call script\setclasspath.bat %%f
rem restore old path here
cd "%OLDDIR%"
"%JAVA_HOME%\bin\java" -Xmx32m -Xms32m -classpath "%CLASSPATH%" org.apache.manifoldcf.crawler.registry.filenet.Filenet
goto done
:nojavahome
echo Environment variable JAVA_HOME is not set properly.
goto done
:nolcfhome
echo Environment variable MCF_HOME is not set properly.
goto done
:done
