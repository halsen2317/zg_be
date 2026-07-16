package com.ccnu.search.api;

import com.ccnu.auth.token.JwtService;
import com.ccnu.search.api.dto.SearchResponse;
import com.ccnu.search.api.dto.SuggestResponse;
import com.ccnu.search.service.SearchService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@Validated
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final JwtService jwtService;

    @GetMapping
    public SearchResponse search(@RequestParam("q") @NotBlank String q,
                                 @RequestParam(defaultValue = "20") @Min(1) int size,
                                 @RequestParam(required = false) String tags,
                                 @RequestParam(required = false) String after,
                                 @AuthenticationPrincipal Jwt jwt) {
        Long uid = jwt != null ? jwtService.extractUserId(jwt) : null;
        return searchService.search(q, size, tags, after, uid);
    }

    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam("prefix") @NotBlank String prefix,
                                   @RequestParam(defaultValue = "10") @Min(1) int size) {
        return searchService.suggest(prefix, size);
    }
}
