package com.fridgemind.controller;

import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 食谱图片静态服务：/recipe-images/{code}/{相对路径}
 * 图片不经拦截器（/api/** 之外），<img> 标签直接可访问
 */
@RestController
public class RecipeImageController {

    /** 爬取数据根目录（scripts 同级的 data/recipes） */
    @Value("${app.recipe.base-dir}")
    private String recipeBaseDir;

    /** code 严格限定哈希格式，防路径穿越 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^rc_[0-9a-f]{12}$");

    @GetMapping("/recipe-images/{code}/{*path}")
    public ResponseEntity<Resource> image(@PathVariable String code, @PathVariable String path) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法食谱标识");
        }
        // 相对路径规范化后必须仍位于该食谱目录内（双保险防穿越）
        Path file = Path.of(recipeBaseDir, code, path).normalize();
        if (!file.startsWith(Path.of(recipeBaseDir, code))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法路径");
        }
        if (!Files.exists(file)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片不存在");
        }
        // 按扩展名给内容类型：爬虫图片为 jpg，png/webp 兜底避免类型不符
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (path.endsWith(".png")) {
            builder.contentType(MediaType.IMAGE_PNG);
        } else if (path.endsWith(".webp")) {
            builder.contentType(MediaType.parseMediaType("image/webp"));
        } else {
            builder.contentType(MediaType.IMAGE_JPEG);
        }
        return builder.body(new FileSystemResource(file));
    }
}
