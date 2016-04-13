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

"%WixPath%\heat.exe" dir "%BinPath%" -gg -dr INSTALLDIR -cg binComp_DCT -var var.binSourcePath -out "%CompilePath%\binComp_DCT.wxs"
IF ERRORLEVEL 1 exit /B 5

"%WixPath%\candle.exe" -ext WixFirewallExtension -out "%CompilePath%\\" -arch %ARCH% -dPlatform="%ARCH%" -dVersionNumber="%VERSION%" -dProjectDir="%FilesPath%" -drootSourcePath="%SourcePath%" -djswSourcePathDoc="%JswDocPath%" -dbinSourcePath="%BinPath%" "%CompilePath%\Product.wxs" "%CompilePath%\WixUI_DCT.wxs" "%CompilePath%\rootComp_DCT.wxs" "%CompilePath%\jswDocComp_DCT.wxs" "%CompilePath%\binComp_DCT.wxs"
IF ERRORLEVEL 1 exit /B 6

"%WixPath%\light.exe" -sice:ICE61 -ext WixFirewallExtension -ext WixUIExtension -ext WiXUtilExtension -out "%DistPath%\OpenDCT_%VERSION%_%ARCH%.msi" -b "%CompilePath%\\" -cultures:en-us -loc "%CompilePath%\Product_en-us.wxl" "%CompilePath%\Product.wixobj" "%CompilePath%\WixUI_DCT.wixobj" "%CompilePath%\rootComp_DCT.wixobj" "%CompilePath%\jswDocComp_DCT.wixobj" "%CompilePath%\binComp_DCT.wixobj"
IF ERRORLEVEL 1 exit /B 7