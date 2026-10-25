package com.guangqian.springkb.dto;

/**
 * 点赞/收藏操作后的最新状态：公开计数 + 当前用户是否已点赞/收藏。
 */
public record InteractionResult(long likeCount, long favoriteCount, boolean liked, boolean favorited) {
}
