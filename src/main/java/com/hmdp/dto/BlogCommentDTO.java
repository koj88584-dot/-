package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogCommentDTO {
    private Long id;
    private Long userId;
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    private LocalDateTime createTime;

    private String userName;
    private String userIcon;
    private Boolean likedByCurrentUser;
    private Boolean owner;
    private Long replyTotal;
}
