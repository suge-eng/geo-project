@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  Geo-Backend 开发环境启动脚本
echo ============================================

if not exist "docker-compose-dev.yml" (
    echo 错误：当前目录不是项目根目录
    pause
    exit /b 1
)

echo 1. 启动开发环境中间件容器...
docker-compose -f docker-compose-dev.yml up -d

echo 等待中间件启动完成...
timeout /t 30 /nobreak > nul

echo 2. 编译项目...
mvn clean package -DskipTests

if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    exit /b 1
)

echo 3. 启动应用（开发环境）...
java -jar target/geo-backend-1.0.0.jar --spring.profiles.active=dev

pause