@echo off
title AI Hall - Start All

echo ============================================
echo    AI Hall - One-Click Start
echo ============================================
echo.

set ROOT=%~dp0

REM ===== Check Java =====
echo [1/4] Checking Java...
if "%JAVA_HOME%"=="" (
    echo [ERROR] JAVA_HOME not found. Please install JDK 21.
    pause
    exit /b 1
)
echo       Java: %JAVA_HOME%
echo.

REM ===== Check Node.js =====
echo [2/4] Checking Node.js...
where node >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found. Please install Node.js.
    pause
    exit /b 1
)
for /f "delims=" %%v in ('node -v') do echo       Node.js: %%v
echo.

REM ===== Check frontend deps =====
echo [3/4] Checking frontend dependencies...
if not exist "%ROOT%student-frontend\node_modules" (
    echo       Installing student frontend dependencies...
    cd /d "%ROOT%student-frontend"
    call npm install
    if errorlevel 1 (
        echo [ERROR] Failed to install student frontend deps
        pause
        exit /b 1
    )
    cd /d "%ROOT%"
) else (
    echo       Student frontend deps: OK
)

if not exist "%ROOT%admin-frontend\node_modules" (
    echo       Installing admin frontend dependencies...
    cd /d "%ROOT%admin-frontend"
    call npm install
    if errorlevel 1 (
        echo [ERROR] Failed to install admin frontend deps
        pause
        exit /b 1
    )
    cd /d "%ROOT%"
) else (
    echo       Admin frontend deps: OK
)
echo.

REM ===== Start services =====
echo [4/4] Starting all services...
echo.

echo       Starting backend (port 8080)...
start "AI Hall - Backend" /D "%ROOT%backend" cmd /k "mvnw.cmd spring-boot:run"

echo       Waiting for backend init...
timeout /t 10 /nobreak >nul

echo       Starting student frontend (port 5173)...
start "AI Hall - Student" /D "%ROOT%student-frontend" cmd /k "npm run dev"

echo       Starting admin frontend (port 5174)...
start "AI Hall - Admin" /D "%ROOT%admin-frontend" cmd /k "npm run dev"

echo.
echo ============================================
echo    All services started!
echo ============================================
echo.
echo   Backend API:  http://localhost:8080/api
echo   Student:      http://localhost:5173
echo   Admin:        http://localhost:5174
echo.
echo   Admin account: admin / 123456
echo   Student: register your own account
echo.
echo   First backend start may take 1-3 min to download Maven deps
echo   Close a window to stop that service
echo.

timeout /t 5 /nobreak >nul
start http://localhost:5173

pause