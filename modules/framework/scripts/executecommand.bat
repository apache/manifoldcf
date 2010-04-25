@echo off
rem check that JAVA_HOME and LCF_HOME are set
rem save existing path here
set OLDDIR=%CD%
cd %LCF_HOME%\processes
set CLASSPATH=.
for %%f in (jar/*) do call script\setclasspath.bat %%f
set JAVADEFINES=
for %%g in (define/*) do call script\setdefine.bat %%g
rem restore old path here
cd %OLDDIR%
"%JAVA_HOME%\bin\java" -Dorg.apache.lcf.configfile=%LCF_HOME%\properties.ini %JAVADEFINES% -classpath %CLASSPATH% %1 %2 %3 %4 %5 %6 %7 %8 %9
