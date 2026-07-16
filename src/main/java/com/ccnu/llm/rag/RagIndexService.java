package com.ccnu.llm.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RAG 索引服务（stub，Commit 14 实现）。
 */
@Service
public class RagIndexService {
    private static final Logger log = LoggerFactory.getLogger(RagIndexService.class);

    public void ensureIndexed(long postId) {
        log.debug("RagIndexService.ensureIndexed({}) - stub", postId);
    }
}