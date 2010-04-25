
for /f "delims=" %%a in ('type %LCF_HOME%\processes\define\%1') do set JAVADEFINES=-D%1=%%a %JAVADEFINES%
