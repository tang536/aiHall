package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 图片落盘服务：管理员端与学生的图片上传共用同一套校验与存储逻辑。
 * 文件保存在运行目录下的 uploads/，由 WebConfig 的 /uploads/** 静态映射对外提供。
 * <p>校验策略：<b>只认文件内容的魔数，不采信客户端传来的后缀名</b>。
 * 只校验后缀挡不住「把 .html 改名成 .png」这类绕过；反过来，落盘时的扩展名
 * 也由探测结果决定，因此 /uploads/ 下不可能出现内容与后缀不一致的文件。
 * <p>上传时顺带生成一张缩略图（{@code <名>_thumb.jpg}）。列表页只加载缩略图，
 * 详情页才加载原图 —— 校园网下用户手机拍的照片动辄 3~5MB，列表一次十几张就是几十 MB。
 * 缩略图命名由 {@link #thumbnailOf} 按约定从原图 URL 推导，因此<b>不需要额外的数据库字段</b>，
 * 历史数据（无缩略图）会自动回落到原图。
 */
@Slf4j
@Service
public class FileStorageService {

    public static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int HEADER_LEN = 16;

    /** 缩略图文件名后缀（拼在扩展名之前） */
    public static final String THUMB_SUFFIX = "_thumb";

    @org.springframework.beans.factory.annotation.Value("${app.upload.thumbnail-max-edge:640}")
    private int thumbnailMaxEdge;

    @org.springframework.beans.factory.annotation.Value("${app.upload.thumbnail-quality:0.8}")
    private float thumbnailQuality;

    /**
     * 由原图 URL 推导缩略图 URL：{@code /uploads/ab12.png} → {@code /uploads/ab12_thumb.jpg}。
     * 拿不到缩略图时（非图片、老数据）调用返回原 URL，前端可以无脑用。
     */
    public static String thumbnailOf(String url) {
        if (url == null || url.isBlank()) return url;
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot <= slash) return url;   // 没有扩展名，认不出来，回落原图
        return url.substring(0, dot) + THUMB_SUFFIX + url.substring(dot);
    }

    /** 上传结果：原图与缩略图 URL */
    public record StoredImage(String url, String thumbnailUrl, int width, int height) {
    }

    /**
     * 保存上传的图片（同时生成缩略图）
     * @return 可访问的相对 URL，如 /uploads/xxx.png
     */
    public String saveImage(MultipartFile file) throws IOException {
        return saveImageDetailed(file).url();
    }

    /** 保存上传的图片，并返回原图 / 缩略图 / 尺寸 */
    public StoredImage saveImageDetailed(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("图片大小不能超过 5MB");
        }

        String type = detectImageType(file);
        if (type == null) {
            throw new BizException("文件内容不是有效的图片，仅支持 jpg/png/gif/webp/bmp");
        }

        Path dir = Paths.get(UPLOAD_DIR);
        if (!Files.exists(dir)) Files.createDirectories(dir);

        String base = UUID.randomUUID().toString().replace("-", "");
        String filename = base + "." + type;
        Path target = dir.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String url = "/uploads/" + filename;
        String thumbUrl = generateThumbnail(target, base, url);
        int[] size = readSize(target);
        return new StoredImage(url, thumbUrl, size[0], size[1]);
    }

    /**
     * 生成缩略图。任何失败都只是「没有缩略图」，绝不让上传失败 ——
     * 比如 webp 不在 JDK 内置解码器范围内，那就直接回落到原图。
     */
    private String generateThumbnail(Path source, String base, String originalUrl) {
        Path thumbPath = Paths.get(UPLOAD_DIR, base + THUMB_SUFFIX + ".jpg");
        try (InputStream in = Files.newInputStream(source)) {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
            if (src == null) return originalUrl;
            java.awt.image.BufferedImage scaled = scale(src, thumbnailMaxEdge);
            // 统一转成 JPEG（不需要透明通道）：同尺寸下比 PNG 小得多，正合列表页的用途
            java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(
                    scaled.getWidth(), scaled.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(scaled, 0, 0, null);
            g.dispose();

            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(thumbnailQuality);
            }
            try (javax.imageio.stream.ImageOutputStream out =
                         javax.imageio.ImageIO.createImageOutputStream(thumbPath.toFile())) {
                writer.setOutput(out);
                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return "/uploads/" + base + THUMB_SUFFIX + ".jpg";
        } catch (Exception e) {
            log.debug("缩略图生成失败，回落到原图（不影响上传）: {}", e.getMessage());
            try {
                Files.deleteIfExists(thumbPath);
            } catch (IOException ignored) {
                // 清理失败无所谓：文件不会被引用
            }
            return originalUrl;
        }
    }

    /** 等比缩放到长边不超过 maxEdge；比 maxEdge 小就原样返回 */
    private java.awt.image.BufferedImage scale(java.awt.image.BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longer = Math.max(w, h);
        if (maxEdge <= 0 || longer <= maxEdge) return src;
        double ratio = (double) maxEdge / longer;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(
                nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    /** 读取像素尺寸，失败返回 {0,0}（仅用于返回给前端做占位，不影响上传） */
    private int[] readSize(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img != null) return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            log.debug("读取图片尺寸失败: {}", e.getMessage());
        }
        return new int[]{0, 0};
    }

    /** 读取文件头并识别图片类型；无法识别返回 null */
    private String detectImageType(MultipartFile file) {
        byte[] h;
        try (InputStream in = file.getInputStream()) {
            h = in.readNBytes(HEADER_LEN);
        } catch (IOException e) {
            return null;
        }
        if (h.length < 2) return null;
        // JPEG: FF D8 FF
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && (h[4] & 0xFF) == 0x0D && (h[5] & 0xFF) == 0x0A && (h[6] & 0xFF) == 0x1A && (h[7] & 0xFF) == 0x0A) {
            return "png";
        }
        // GIF: GIF87a / GIF89a
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return "gif";
        }
        // BMP: BM
        if (h[0] == 'B' && h[1] == 'M') {
            return "bmp";
        }
        // WebP: RIFF....WEBP
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
