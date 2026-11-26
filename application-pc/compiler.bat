@echo off
setlocal
REM ============================================================
REM  compiler.bat - compile l'application PC sans Maven (Windows)
REM
REM  A lancer depuis le dossier application-pc (double-clic possible).
REM  - Compile toutes les sources de src\main\java vers build\classes
REM  - Copie les ressources (images) de src\main\resources
REM
REM  -encoding UTF-8 : indispensable pour les accents des textes Java.
REM  --release 17    : code compatible Java 17 et toutes les versions
REM                    suivantes (evite UnsupportedClassVersionError).
REM ============================================================
cd /d "%~dp0"

set GSON=lib\gson-2.10.1.jar
if not exist "%GSON%" (
  echo *** Bibliotheque Gson introuvable : %GSON%
  echo *** Verifiez que le dossier lib contient gson-2.10.1.jar.
  echo.
  pause
  exit /b 1
)

echo Compilation de l'application...
if not exist build\classes mkdir build\classes

REM Liste tous les .java dans un fichier temporaire (evite une ligne trop longue)
dir /s /b src\main\java\*.java > "%TEMP%\sources_tournoi.txt"

javac --release 17 -encoding UTF-8 -cp "%GSON%" -d build\classes "@%TEMP%\sources_tournoi.txt"
if errorlevel 1 (
  echo.
  echo *** La compilation a echoue : voir les messages ci-dessus.
  echo.
  pause
  exit /b 1
)

REM Ressources (image de la table, etc.) chargees par getResourceAsStream
xcopy /e /i /y /q src\main\resources build\classes > nul

echo.
echo Compilation OK. Classes dans build\classes.
echo.
echo Pour lancer l'application :
echo   java -cp "build\classes;%GSON%" org.club.tournoi.ui.Application
echo.
pause
endlocal
