@echo off
echo ==============================================
echo   Agenda Devocional - Deploy Cloudflare
echo ==============================================
echo.
echo Verificando login no Cloudflare...
call npx wrangler whoami
if %errorlevel% neq 0 (
    echo.
    echo Voces nao estao logados no Wrangler. 
    echo Por favor, faça login executando: npx wrangler login
    echo.
    pause
    exit /b 1
)

echo Iniciando processo de Deploy...
python tradutor\scripts\deploy_cloudflare.py
echo.
echo Deploy concluído!
pause
