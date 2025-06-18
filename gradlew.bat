@echo off
@rem --------------------------------------------------------------------------
@rem
@rem  Gradle startup script for Windows
@rem
@rem --------------------------------------------------------------------------

setlocal

set DIR=%~dp0

set APP_BASE_NAME=%~n0
set APP_HOME=%DIR%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

set DEFAULT_JVM_OPTS=

"%JAVA_HOME%\bin\java" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
