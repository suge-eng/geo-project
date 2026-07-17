package com.geo.enums;

public enum AiPlatform {

    DOUBAO("doubao", "豆包"),
    WENXIN("wenxin", "文心一言"),
    DEEPSEEK("deepseek", "DeepSeek"),
    CHATGPT("chatgpt", "ChatGPT"),
    CLAUDE("claude", "Claude"),
    QIANWEN("qianwen", "通义千问"),
    ERNIE("ernie", "百度ERNIE");

    private final String code;
    private final String displayName;

    AiPlatform(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AiPlatform fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AiPlatform platform : values()) {
            if (platform.code.equalsIgnoreCase(code)) {
                return platform;
            }
        }
        return null;
    }
}