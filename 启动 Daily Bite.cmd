@echo off
chcp 65001 >nul
title CS Learning OS Daily Bite

set "ROOT=%~dp0"
pushd "%ROOT%" >nul

if "%~1"=="" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%ROOT%scripts\start-bite.ps1"
) else (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%ROOT%scripts\start-bite.ps1" -DataRoot "%~1"
)
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%
