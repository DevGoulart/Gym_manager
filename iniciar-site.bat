@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
title Força Total - iniciar o site

rem =====================================================================
rem  Força Total - inicia o site com dois cliques
rem
rem  1. Se o site já estiver no ar, só abre o navegador.
rem  2. Procura o Java 21 (JAVA_HOME, pastas comuns e os JDKs do IntelliJ).
rem  3. Procura o Maven (PATH, o Maven que o IntelliJ baixa em .m2\wrapper
rem     ou o que vem dentro do IntelliJ).
rem  4. Abre o servidor numa janela própria (feche-a para parar o site),
rem     espera responder e abre http://localhost:8080 no navegador.
rem
rem  Outra porta:  iniciar-site.bat 9090
rem  (Para testes: FT_TESTE=1 não abre o navegador e minimiza a janela do servidor;
rem   FT_ARGS passa argumentos extras ao servidor.)
rem =====================================================================

set "PORTA=8080"
if not "%~1"=="" set "PORTA=%~1"
set "URL=http://localhost:%PORTA%/"
set "PASTA=%~dp0backend"
set "JANELA="
if "%FT_TESTE%"=="1" set "JANELA=/min"

echo.
echo   FORÇA TOTAL - Sistema de Gestão de Academia
echo   -------------------------------------------
echo.

if not exist "%PASTA%\pom.xml" (
  echo   [erro] Não achei a pasta "backend" ao lado deste arquivo.
  echo          Deixe o iniciar-site.bat na pasta principal do projeto.
  goto :falhou
)

rem ---- 1. Já está no ar? -------------------------------------------------
call :respondendo
if "!NO_AR!"=="1" (
  echo   O site já está rodando em %URL%
  echo   Abrindo o navegador...
  if not "%FT_TESTE%"=="1" start "" "%URL%"
  goto :fim
)

rem A porta pode estar ocupada por outro programa
netstat -ano | findstr /r /c:":%PORTA% .*LISTENING" >nul
if not errorlevel 1 (
  echo   [erro] A porta %PORTA% já está sendo usada por outro programa.
  echo          Feche esse programa ou use outra porta:  iniciar-site.bat 9090
  goto :falhou
)

rem ---- 2. Java 21 ou mais novo ---------------------------------------------
set "JAVA_OK="
if defined JAVA_HOME call :testar_java "%JAVA_HOME%"
if not defined JAVA_OK for /d %%J in ("%ProgramFiles%\Java\jdk-2*" "%ProgramFiles%\Eclipse Adoptium\jdk-2*" "%ProgramFiles%\Microsoft\jdk-2*" "%ProgramFiles%\Amazon Corretto\jdk2*" "%USERPROFILE%\.jdks\*") do (
  if not defined JAVA_OK call :testar_java "%%~fJ"
)
if not defined JAVA_OK (
  echo   [erro] Não encontrei o Java 21 ^(ou mais novo^).
  echo          Instale o JDK 21 ^(https://adoptium.net^) ou configure a variável JAVA_HOME.
  goto :falhou
)
set "JAVA_HOME=%JAVA_OK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo   Java:  %JAVA_HOME%

rem ---- 3. Maven --------------------------------------------------------------
set "MVN="
for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined MVN set "MVN=%%M"
if not defined MVN if exist "%USERPROFILE%\.m2\wrapper\dists" (
  for /r "%USERPROFILE%\.m2\wrapper\dists" %%M in (mvn.cmd) do if exist "%%M" set "MVN=%%M"
)
if not defined MVN for /d %%I in ("%ProgramFiles%\JetBrains\IntelliJ*" "%LOCALAPPDATA%\Programs\IntelliJ*" "%LOCALAPPDATA%\JetBrains\Toolbox\apps\IDEA*\*\*") do (
  if exist "%%~fI\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN=%%~fI\plugins\maven\lib\maven3\bin\mvn.cmd"
)
if not defined MVN (
  echo   [erro] Não encontrei o Maven.
  echo          Abra o projeto uma vez no IntelliJ ^(ele baixa o Maven^) ou instale o Maven
  echo          ^(https://maven.apache.org^) e coloque a pasta bin no PATH.
  goto :falhou
)
echo   Maven: %MVN%

rem ---- 4. Sobe o servidor numa janela própria --------------------------------
echo.
echo   Iniciando o servidor na porta %PORTA%. A primeira vez pode levar alguns minutos.
echo   Uma janela "Força Total - servidor" vai abrir: feche-a para parar o site.
start "Força Total - servidor (feche esta janela para parar o site)" %JANELA% /d "%PASTA%" cmd /k call "%MVN%" -q spring-boot:run "-Dspring-boot.run.arguments=--server.port=%PORTA% %FT_ARGS%"

rem Espera responder (até 4 minutos; o ping só serve de pausa de ~2 s e funciona até sem teclado)
set /a TENTATIVAS=0
<nul set /p "=  Aguardando o servidor "
:esperar
ping -n 3 127.0.0.1 >nul
set /a TENTATIVAS+=1
<nul set /p "=."
call :respondendo
if "!NO_AR!"=="1" goto :no_ar
if !TENTATIVAS! lss 120 goto :esperar
echo.
echo   [erro] O servidor não respondeu em 4 minutos.
echo          Veja a mensagem de erro na janela "Força Total - servidor".
goto :falhou

:no_ar
echo.
echo.
echo   Pronto: o site está no ar.
echo     Apresentação do produto:  %URL%
echo     Sistema:                  %URL%app
echo.
if not "%FT_TESTE%"=="1" start "" "%URL%"
goto :fim

rem =====================================================================
rem  Sub-rotinas
rem =====================================================================

rem Define NO_AR=1 se o site responder na porta escolhida
:respondendo
set "NO_AR=0"
set "CODIGO="
where curl.exe >nul 2>nul
if not errorlevel 1 (
  for /f %%C in ('curl.exe -s -o nul -m 3 -w "%%{http_code}" "%URL%" 2^>nul') do set "CODIGO=%%C"
) else (
  for /f %%C in ('powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 '%URL%').StatusCode } catch { 0 }"') do set "CODIGO=%%C"
)
if "!CODIGO!"=="200" set "NO_AR=1"
exit /b 0

rem Aceita a pasta se tiver bin\java.exe na versão 21 ou mais nova
:testar_java
if not exist "%~1\bin\java.exe" exit /b 0
"%~1\bin\java.exe" -version 2>&1 | findstr /r /c:"version \"2[1-9]" /c:"version \"[3-9][0-9]" >nul
if not errorlevel 1 set "JAVA_OK=%~1"
exit /b 0

:falhou
echo.
pause
exit /b 1

:fim
echo.
echo   Esta janela fecha em alguns segundos...
ping -n 7 127.0.0.1 >nul
exit /b 0
