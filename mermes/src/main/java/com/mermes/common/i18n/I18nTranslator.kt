package com.mermes.common.i18n

import java.util.Locale

interface I18nTranslator {
    /**
     * 将底层命令或网络的原始错误文本翻译为指定语言的友好描述
     * @param rawError 底层抛出的异常描述或 Shell stderr 原始输出
     * @param locale 目标语言（目前支持 "zh" 和 "en"）
     * @return 翻译后的本地化错误提示
     */
    fun translate(rawError: String, locale: Locale): String
}
