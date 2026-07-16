package com.ccnu.knowpost.api;

import com.ccnu.knowpost.api.dto.DescriptionSuggestRequest;
import com.ccnu.knowpost.api.dto.DescriptionSuggestResponse;
import com.ccnu.llm.service.KnowPostDescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/v1/knowposts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class KnowPostAiController {

    private final KnowPostDescriptionService descriptionService;

    /** 生成 ≤50 字中文摘要。 */
    @PostMapping(path = "/description/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DescriptionSuggestResponse suggest(@Valid @RequestBody DescriptionSuggestRequest req) {
        return new DescriptionSuggestResponse(descriptionService.generateDescription(req.content()));
    }
}
