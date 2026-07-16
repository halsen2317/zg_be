package com.ccnu.search.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 搜索索引服务（stub，Commit 12 实现）。
 */
@Service
public class SearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);

    public void upsertKnowPost(long id) {
        log.debug("SearchIndexService.upsertKnowPost({}) - stub", id);
    }

    public void softDeleteKnowPost(long id) {
        log.debug("SearchIndexService.softDeleteKnowPost({}) - stub", id);
    }
}