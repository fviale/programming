@echo off

SETLOCAL
IF NOT DEFINED PROACTIVE set PROACTIVE=%CD%\..\..\..

call "%PROACTIVE%\scripts\windows\init.bat"

%JAVA_CMD% -Xms64m -Xmx1024m org.objectweb.proactive.core.body.ft.servers.StartFTServer %1 %2 %3 %4 %5 %6

ENDLOCAL
pause