@echo off

:: This does not need to be defined unless you did not use the Wix installer
:: or the installer didn't set the environment variable WIX.
SET WixPath=C:\wix

:: If Wix is not installed on this computer, we can't build anything.
IF NOT DEFINED WIX (

:: Check if we have manually specified the path before returning an error.
IF NOT DEFINED WixPath (
	echo FATAL: Wix does not appear to be installed.
	echo If you just installed Wix, try re-opening the terminal and/or your IDE.
	exit /B 1
)
)

IF DEFINED WixPath (
	goto VerifyPath
)

SET WixPath=%WIX%bin

:VerifyPath
IF NOT EXIST "%WixPath%\heat.exe" (
	echo FATAL: heat.exe is missing. You must install and set the binary path for Wix before you can build any Windows installers.
	exit /B 1
)

IF NOT EXIST "%WixPath%\candle.exe" (
	echo FATAL: candle.exe is missing. You must install and set the binary path for Wix before you can build any Windows installers.
	exit /B 1
)

IF NOT EXIST "%WixPath%\light.exe" (
	echo FATAL: light.exe is missing. You must install and set the binary path for Wix before you can build any Windows installers.
	exit /B 1
)

SET FilesPath=%~dp0..\..\build
SET CompilePath=%FilesPath%\wix\compile
SET SourcePath=%FilesPath%\wix\source
SET ConfigPath=%FilesPath%\wix\config
SET DistPath=%FilesPath%\distributions
SET JswDocPath=%FilesPath%\jsw\doc
