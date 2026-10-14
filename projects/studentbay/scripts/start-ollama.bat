@echo off
chcp 65001 >nul
echo ============================================
echo   StudentBay AI 描述优化 · Ollama 启动助手
echo ============================================
echo.
echo 本脚本用于启用"AI 一键优化商品描述"（可选免费功能）：
echo   - 检测 Ollama 是否安装
echo   - 拉取本地模型 qwen2.5:3b（约 2GB，首次需要几分钟）
echo   - 启动 Ollama 服务并等待就绪
echo.
echo 不安装 Ollama 也不影响平台任何功能（AI 默认关闭，发布流程完全不受影响）。
echo.

where ollama >nul 2>nul
if errorlevel 1 (
    echo [未找到] 未检测到 Ollama，请先安装：
    echo   官网下载：https://ollama.com/download
    echo   安装完成后重新运行本脚本。
    pause
    exit /b 1
)

echo [1/3] 检查模型 qwen2.5:3b ...
ollama list 2>nul | findstr /C:"qwen2.5:3b" >nul
if errorlevel 1 (
    echo   模型不存在，开始拉取（约 2GB，请耐心等待）...
    ollama pull qwen2.5:3b
    if errorlevel 1 (
        echo [失败] 模型拉取失败，请检查网络后重试。
        pause
        exit /b 1
    )
)

echo [2/3] 启动 Ollama 服务 ...
start "" ollama serve

echo [3/3] 等待服务就绪（最多 15 秒）...
for /l %%i in (1,1,15) do (
    curl -s -o nul http://localhost:11434/api/tags 2>nul && goto ready
    timeout /t 1 >nul
)
echo [失败] Ollama 服务未就绪，请手动运行 ollama serve 后重试。
pause
exit /b 1

:ready
echo.
echo [完成] Ollama 已就绪！模型：qwen2.5:3b
echo.
echo 接下来启用 AI：
echo   1. 在 IDEA 运行配置中设置环境变量 AI_DESC_ENABLED=true
echo   2. 重启 Spring Boot 服务
echo   3. 打开发布页，商品描述框右上角即可使用"AI 优化描述"
echo.
pause
