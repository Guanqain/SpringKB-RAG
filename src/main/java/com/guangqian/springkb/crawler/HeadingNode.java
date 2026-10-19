package com.guangqian.springkb.crawler;

/**
 * 文档标题节点（分块锚点）：后续分块器按标题层级切分，而非按字符数盲切。
 */
public record HeadingNode(int level, String text, String anchorId) {
}
