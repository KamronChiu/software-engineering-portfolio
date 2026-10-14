package com.campus.trade.controller.product;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.service.product.NaturalLanguageSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class NaturalLanguageSearchController {
    private final NaturalLanguageSearchService search;

    public NaturalLanguageSearchController(NaturalLanguageSearchService search) {
        this.search = search;
    }

    @PostMapping("/natural-search")
    public ApiResponse<Map<String, Object>> search(@Valid @RequestBody NaturalSearchRequest request) {
        return ApiResponse.success(search.search(request.query()));
    }

    public record NaturalSearchRequest(@NotBlank @Size(max = 300) String query) {}
}
