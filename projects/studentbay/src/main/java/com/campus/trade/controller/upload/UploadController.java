package com.campus.trade.controller.upload;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通用图片上传：发布页"本地文件上传"通道。
 * 复用头像上传的安全模式（≤5MB、jpg/jpeg/png/webp/gif 白名单、写入 ./uploads、返回 /uploads/{文件名}）。
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    private final CurrentUserService currentUser;

    public UploadController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file, HttpSession session) {
        currentUser.require(session);
        if (file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "图片不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!Set.of("jpg", "jpeg", "png", "webp", "gif").contains(ext)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 jpg、png、webp、gif 格式的图片");
        }
        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            String fileName = "product-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 9000 + 1000) + "." + ext;
            file.transferTo(dir.resolve(fileName));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", "/uploads/" + fileName);
            return ApiResponse.success(200, "上传成功", result);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败，请重试");
        }
    }

    /**
     * 粘贴图片链接：由服务端代理下载并保存到本地，绕开外部图床防盗链/跨域导致前端无法显示的问题。
     * POST /api/uploads/by-url  body: {"url": "https://..."}
     */
    @PostMapping("/by-url")
    public ApiResponse<Map<String, Object>> uploadByUrl(@RequestBody Map<String, String> body, HttpSession session) {
        currentUser.require(session);
        String url = body == null ? null : body.get("url");
        if (url == null || url.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写图片链接");
        }
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 http/https 图片链接");
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "图片链接格式不正确");
        }
        if (host == null || host.isBlank() || isPrivateHost(host)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该图片链接不可访问");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (StudentBay image proxy)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "图片链接无法访问（状态码 " + response.statusCode() + "）");
            }
            byte[] bytes = response.body();
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "图片文件不能超过 5MB");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String ext = extOf(contentType, url);
            if (ext == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "该链接不是有效的图片地址");
            }
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            String fileName = "product-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 9000 + 1000) + "." + ext;
            Files.write(dir.resolve(fileName), bytes);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", "/uploads/" + fileName);
            return ApiResponse.success(200, "图片已保存", result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "图片下载失败，请检查链接是否有效");
        }
    }

    private String extOf(String contentType, String url) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return "jpg";
        }
        if (lower.contains("png")) {
            return "png";
        }
        if (lower.contains("webp")) {
            return "webp";
        }
        if (lower.contains("gif")) {
            return "gif";
        }
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains(".jpg") || u.contains(".jpeg")) {
            return "jpg";
        }
        if (u.contains(".png")) {
            return "png";
        }
        if (u.contains(".webp")) {
            return "webp";
        }
        if (u.contains(".gif")) {
            return "gif";
        }
        return null;
    }

    private boolean isPrivateHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(h);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            byte[] raw = address.getAddress();
            if (raw.length != 4) {
                return true;
            }
            int first = raw[0] & 0xff;
            if (first == 10 || first == 127) {
                return true;
            }
            if (first == 192 && (raw[1] & 0xff) == 168) {
                return true;
            }
            if (first == 172 && (raw[1] & 0xff) >= 16 && (raw[1] & 0xff) <= 31) {
                return true;
            }
            if (first == 169 && (raw[1] & 0xff) == 254) {
                return true;
            }
        } catch (Exception exception) {
            return true;
        }
        return false;
    }
}
