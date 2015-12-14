@echo off
setlocal

echo --------------------
echo Wrapper Build System
echo --------------------

set OLD_ANT_HOME=%ANT_HOME%

if not "%WRAPPER_TOOLS%"=="" goto runAnt

set WRAPPER_TOOLS=tools/apache-ant-1.6.2

:runAnt
set ANT_HOME=%WRAPPER_TOOLS%
call %WRAPPER_TOOLS%\bin\ant.bat -logger org.apache.tools.ant.NoBannerLogger -emacs -Dtools.dir=%WRAPPER_TOOLS% -Dbits=32 %1 %2 %3 %4 %5 %6 %7 %8

:end
set ANT_HOME=%OLD_ANT_HOME%
set OLD_ANT_HOME=

