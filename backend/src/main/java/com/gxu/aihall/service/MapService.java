package com.gxu.aihall.service;

import com.gxu.aihall.entity.Location;
import com.gxu.aihall.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 校园地图服务
 */
@Service
public class MapService {

    private final LocationRepository locationRepository;

    public MapService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public List<Location> getAllLocations() {
        return locationRepository.findAllByOrderBySortOrderAsc();
    }

    public List<Location> getLocationsByCategory(String category) {
        return locationRepository.findByCategoryOrderBySortOrderAsc(category);
    }

    public List<Location> searchLocations(String keyword) {
        return locationRepository.findByNameContainingIgnoreCase(keyword);
    }

    public Location getLocationById(Long id) {
        return locationRepository.findById(id).orElse(null);
    }

    /**
     * 路径规划（简化版：计算两点间距离和预计时间）
     */
    public String planRoute(Long fromId, Long toId) {
        Location from = locationRepository.findById(fromId).orElse(null);
        Location to = locationRepository.findById(toId).orElse(null);
        if (from == null || to == null) return "无法规划路径";

        double distance = calculateDistance(from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude());
        int walkTime = (int) (distance / 80 * 60); // 假设步行速度80m/min

        return String.format("从%s到%s，距离约%.0f米，步行约%d分钟。路线：沿校园主道直行，途经%s。",
                from.getName(), to.getName(), distance, walkTime,
                distance > 200 ? "中心花园" : "附近建筑");
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return 100;
        // 简化的欧几里得距离（校园范围内足够）
        double dx = (lon2 - lon1) * 111000 * Math.cos(Math.toRadians(lat1));
        double dy = (lat2 - lat1) * 111000;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ==================== 管理员地点管理 ====================

    public Location createLocation(Location location) {
        if (location.getSortOrder() == null) {
            long count = locationRepository.count();
            location.setSortOrder((int) count + 1);
        }
        return locationRepository.save(location);
    }

    public Location updateLocation(Long id, Location location) {
        Location existing = locationRepository.findById(id).orElse(null);
        if (existing == null) return null;

        if (location.getName() != null) existing.setName(location.getName());
        if (location.getCategory() != null) existing.setCategory(location.getCategory());
        if (location.getAddress() != null) existing.setAddress(location.getAddress());
        if (location.getLongitude() != null) existing.setLongitude(location.getLongitude());
        if (location.getLatitude() != null) existing.setLatitude(location.getLatitude());
        if (location.getDescription() != null) existing.setDescription(location.getDescription());
        if (location.getOpenHours() != null) existing.setOpenHours(location.getOpenHours());
        if (location.getPhone() != null) existing.setPhone(location.getPhone());
        if (location.getImageUrl() != null) existing.setImageUrl(location.getImageUrl());
        if (location.getFloor() != null) existing.setFloor(location.getFloor());
        if (location.getBuilding() != null) existing.setBuilding(location.getBuilding());
        if (location.getSortOrder() != null) existing.setSortOrder(location.getSortOrder());

        return locationRepository.save(existing);
    }

    public boolean deleteLocation(Long id) {
        if (!locationRepository.existsById(id)) return false;
        locationRepository.deleteById(id);
        return true;
    }

    public long countLocations() {
        return locationRepository.count();
    }
}
