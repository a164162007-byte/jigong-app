#!/bin/bash

# 记工App Android项目构建脚本
# 需要Android Studio或Gradle已安装

echo "=================================="
echo "  记工App Android 构建脚本"
echo "=================================="
echo ""

# 检查Java
if ! command -v java &> /dev/null; then
    echo "错误: 未检测到Java环境"
    echo "请安装 JDK 17 或更高版本"
    exit 1
fi

# 检查Gradle
if command -v gradle &> /dev/null; then
    echo "检测到Gradle，使用系统Gradle编译..."
    gradle assembleDebug
elif [ -f "./gradlew" ]; then
    echo "使用项目Gradle Wrapper编译..."
    chmod +x ./gradlew
    ./gradlew assembleDebug
else
    echo "错误: 未检测到Gradle"
    echo "请选择以下方式之一:"
    echo "1. 安装 Android Studio (推荐): https://developer.android.com/studio"
    echo "2. 安装 Gradle: https://gradle.org/install/"
    exit 1
fi

echo ""
echo "构建完成!"
echo "APK文件位于: app/build/outputs/apk/debug/"
