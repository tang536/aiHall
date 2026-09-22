@echo off
title AI Hall - Stop All

echo ============================================
echo    Stopping all AI Hall services
echo ============================================
echo.

echo Stopping backend (Java)...
taskkill /F /IM java.exe 2>nul

echo Stopping frontend (Node)...
taskkill /F /IM node.exe 2>nul

echo.
echo ============================================
echo    All services stopped
echo ============================================
echo.
pause