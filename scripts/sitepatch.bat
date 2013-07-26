"%JAVA_HOME%"\bin\java -jar c:\javadocpatcher\JavadocUpdaterTool.jar -R %1\release\trunk\api
"%JAVA_HOME%"\bin\java -jar c:\javadocpatcher\JavadocUpdaterTool.jar -R %1\release\release-1.2\api
"%JAVA_HOME%"\bin\java -jar c:\javadocpatcher\JavadocUpdaterTool.jar -R %1\release\release-1.1.1\api
"%JAVA_HOME%"\bin\java -jar c:\javadocpatcher\JavadocUpdaterTool.jar -R %1\release\release-1.0.1\api
"%JAVA_HOME%"\bin\java -jar c:\javadocpatcher\JavadocUpdaterTool.jar -R %1\release\release-0.6\api
del /s %1\*.orig

