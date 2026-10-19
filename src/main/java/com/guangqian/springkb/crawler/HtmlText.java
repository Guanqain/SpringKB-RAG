package com.guangqian.springkb.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 正文提取小工具：多选择器兜底 + 空白归一化 */
final class HtmlText {

    private HtmlText() {
    }

    /** 依次尝试选择器，取文本长度达标者；全部失败则退回 body 全文 */
    static String extract(Document doc, String... selectors) {
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String t = normalize(el.text());
                if (t.length() > 300) {
                    return t;
                }
            }
        }
        return normalize(doc.body().text());
    }

    static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
