@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET ___MVNW_SWITCH_DASH_DASH=--
@SET ___MVNW_SWITCH_DASH=-
@SETLOCAL
%__MVNW_ARG0_NAME__% >NUL 2>NUL
@IF ERRORLEVEL 1 SET __MVNW_ARG0_NAME__=mvnw.cmd

@SET MAVEN_PROJECTBASEDIR=%~dp0
@IF NOT "%MAVEN_BASEDIR%" == "" SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@SET MAVEN_WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
@SET MAVEN_WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"
@SET DOWNLOAD_URL=

@FOR /F "usebackq tokens=1,2 delims==" %%A IN (%MAVEN_WRAPPER_PROPERTIES%) DO (
  @IF "%%A"=="distributionUrl" SET DISTRIBUTION_URL=%%B
  @IF "%%A"=="wrapperUrl" SET DOWNLOAD_URL=%%B
)

@SET JAVA_HOME_CANDIDATE=%JAVA_HOME%
@IF NOT "%JAVA_HOME_CANDIDATE%" == "" (
  @SET JAVA_EXEC=%JAVA_HOME_CANDIDATE%\bin\java.exe
  @IF NOT EXIST "%JAVA_EXEC%" SET JAVA_EXEC=%JAVA_HOME_CANDIDATE%\bin\java
) ELSE (
  @SET JAVA_EXEC=java
)

@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET WRAPPER_ARGS=%*

@"%JAVA_EXEC%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR:~0,-1%" -cp %MAVEN_WRAPPER_JAR% %WRAPPER_LAUNCHER% %WRAPPER_ARGS%
@SET MVNW_RESULT=%ERRORLEVEL%
@ENDLOCAL
@EXIT /B %MVNW_RESULT%
