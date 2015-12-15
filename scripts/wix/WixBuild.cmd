@echo off

IF "%1"=="" (
	echo FATAL: You must specify an architecture.
	echo Ex: WixBuild.cmd x64 1.0
	exit /B 2
)

IF "%2"=="" (
	echo FATAL: You must specify a version.
	echo Ex: WixBuild.cmd x64 1.0
	exit /B 2
)

CALL %~dp0WixConfig.cmd
IF ERRORLEVEL 1 exit /B 1

SET ARCH=%1
SET VERSION=%2


"%WixPath%\heat.exe" dir "%SourcePath%" -srd -gg -dr INSTALLDIR -cg rootComp_DCT -var var.rootSourcePath -out "%CompilePath%\rootComp_DCT.wxs"
IF ERRORLEVEL 1 exit /B 3


"%WixPath%\heat.exe" dir "%JswDocPath%" -gg -dr jswDir -cg jswDocComp_DCT -var var.jswSourcePathDoc -out "%CompilePath%\jswDocComp_DCT.wxs"
IF ERRORLEVEL 1 exit /B 4

"%WixPath%\candle.exe" -out "%CompilePath%\\" -arch %ARCH% -dPlatform="%ARCH%" -dVersionNumber="%VERSION%" -dProjectDir="%FilesPath%" -djswSourcePathDoc="%JswDocPath%" -drootSourcePath="%SourcePath%" "%CompilePath%\Product.wxs" "%CompilePath%\WixUI_DCT.wxs" "%CompilePath%\jswDocComp_DCT.wxs" "%CompilePath%\rootComp_DCT.wxs"
IF ERRORLEVEL 1 exit /B 5

"%WixPath%\light.exe" -sice:ICE61 -out "%DistPath%\OpenDCT_%VERSION%_%ARCH%.msi" -b "%CompilePath%\\" -ext WixUIExtension -ext WiXUtilExtension -ext WixFirewallExtension -cultures:en-us -loc "%CompilePath%\Product_en-us.wxl" "%CompilePath%\Product.wixobj" "%CompilePath%\WixUI_DCT.wixobj" "%CompilePath%\jswDocComp_DCT.wixobj" "%CompilePath%\rootComp_DCT.wixobj"
IF ERRORLEVEL 1 exit /B 6