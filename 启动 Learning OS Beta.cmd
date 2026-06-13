@echo off
chcp 65001 >nul
title CS Learning OS Beta

set "ROOT=%~dp0"
pushd "%ROOT%" >nul

powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%ROOT%scripts\start-beta.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%
