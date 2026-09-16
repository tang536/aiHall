package com.gxu.aihall.service;

import com.gxu.aihall.entity.Notification;
import com.gxu.aihall.repository.NotificationRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知公告服务
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getAll() {
        return notificationRepository.findAllByOrderByIsTopDescPublishTimeDesc();
    }

    public List<Notification> getByCategory(String category) {
        return notificationRepository.findByCategoryOrderByIsTopDescPublishTimeDesc(category);
    }

    public List<Notification> search(String keyword) {
        return notificationRepository.findByTitleContainingIgnoreCaseOrderByIsTopDescPublishTimeDesc(keyword);
    }

    public Notification getById(Long id) {
        Notification n = notificationRepository.findById(id).orElse(null);
        if (n != null) {
            n.setViewCount(n.getViewCount() + 1);
            notificationRepository.save(n);
        }
        return n;
    }

    /**
     * 获取紧急通知
     */
    public List<Notification> getEmergencyNotifications() {
        return notificationRepository.findByIsEmergencyTrueAndStatusOrderByPublishTimeDesc(1);
    }

    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }

    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }

    /**
     * 导入 PDF 格式的通知：解析 PDF 文本作为正文，标题默认为文件名（可指定），按标题去重。
     * @param file       PDF 文件
     * @param title      通知标题（可空，空则取文件名）
     * @param category   通知类别（可空，默认"校园公告"）
     * @param department 发布部门（可空，默认"PDF导入"）
     * @return 保存后的通知
     */
    public Notification importPdf(MultipartFile file, String title, String category, String department) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择要导入的 PDF 文件");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new RuntimeException("只支持 PDF 格式文件");
        }

        // 提取 PDF 文本
        String text;
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(doc);
        } catch (Exception e) {
            throw new RuntimeException("PDF 解析失败：" + e.getMessage());
        }
        if (text == null || text.trim().isEmpty()) {
            throw new RuntimeException("未能从 PDF 中提取到文字（可能是扫描件/图片型 PDF，请先转换为文字型 PDF）");
        }
        text = text.trim();

        // 标题：优先用传入标题，否则取文件名（去掉 .pdf 后缀）
        String finalTitle = (title != null && !title.trim().isEmpty())
                ? title.trim()
                : originalName.replaceAll("(?i)\\.pdf$", "").trim();
        if (finalTitle.isEmpty()) finalTitle = "PDF导入通知";
        if (finalTitle.length() > 200) finalTitle = finalTitle.substring(0, 200);

        if (notificationRepository.existsByTitle(finalTitle)) {
            throw new RuntimeException("已存在相同标题的通知「" + finalTitle + "」，请更换标题后重试");
        }

        Notification n = new Notification();
        n.setTitle(finalTitle);
        n.setContent(text);
        n.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : "校园公告");
        n.setDepartment(department != null && !department.trim().isEmpty() ? department.trim() : "PDF导入");
        n.setIsTop(false);
        n.setIsEmergency(false);
        n.setSummary(buildSummary(text));
        n.setViewCount(0);
        n.setStatus(1);
        n.setPublishTime(LocalDateTime.now());
        n.setCreateTime(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    /** 从正文生成摘要（取前 150 字） */
    private String buildSummary(String text) {
        String plain = text.replaceAll("\\s+", " ").trim();
        return plain.length() > 150 ? plain.substring(0, 150) + "..." : plain;
    }
}
