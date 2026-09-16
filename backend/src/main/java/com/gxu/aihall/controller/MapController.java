package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.Location;
import com.gxu.aihall.service.MapService;
import com.gxu.aihall.service.RoadNetworkService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;
    private final RoadNetworkService roadNetworkService;

    public MapController(MapService mapService, RoadNetworkService roadNetworkService) {
        this.mapService = mapService;
        this.roadNetworkService = roadNetworkService;
    }

    // ==================== 地点数据接口 ====================

    @GetMapping("/locations")
    public Result<List<Location>> getAllLocations() {
        return Result.success(mapService.getAllLocations());
    }

    @GetMapping("/locations/category/{category}")
    public Result<List<Location>> getByCategory(@PathVariable String category) {
        return Result.success(mapService.getLocationsByCategory(category));
    }

    @GetMapping("/locations/search")
    public Result<List<Location>> search(@RequestParam String keyword) {
        return Result.success(mapService.searchLocations(keyword));
    }

    @GetMapping("/locations/{id}")
    public Result<Location> getById(@PathVariable Long id) {
        Location loc = mapService.getLocationById(id);
        if (loc == null) return Result.error("地点不存在");
        return Result.success(loc);
    }

    @GetMapping("/route")
    public Result<String> planRoute(@RequestParam Long from, @RequestParam Long to) {
        return Result.success(mapService.planRoute(from, to));
    }

    /**
     * 校园路网数据（节点 + 边）。前端离线导航从本接口按需拉取，
     * 替代原先随前端打包的 public/map-data/road-network.json。
     * 后端内存缓存，几乎不额外开销；加载失败时前端有「直线距离」降级。
     */
    @GetMapping("/road-network")
    public Result<Map<String, Object>> roadNetwork() {
        Map<String, Object> data = roadNetworkService.roadNetwork();
        if (data == null) return Result.error("路网数据暂不可用");
        return Result.success(data);
    }

    // ==================== 管理员地点管理 ====================

    /**
     * 管理员添加地点
     */
    @PostMapping("/admin/locations")
    public Result<Location> adminCreateLocation(@RequestBody Location location) {
        return Result.success(mapService.createLocation(location));
    }

    /**
     * 管理员更新地点
     */
    @PutMapping("/admin/locations/{id}")
    public Result<Location> adminUpdateLocation(@PathVariable Long id, @RequestBody Location location) {
        Location updated = mapService.updateLocation(id, location);
        if (updated == null) return Result.error("地点不存在");
        return Result.success(updated);
    }

    /**
     * 管理员删除地点
     */
    @DeleteMapping("/admin/locations/{id}")
    public Result<String> adminDeleteLocation(@PathVariable Long id) {
        boolean success = mapService.deleteLocation(id);
        if (!success) return Result.error("地点不存在");
        return Result.success("删除成功");
    }

    /**
     * 管理员获取地点列表（带分页）
     */
    @GetMapping("/admin/locations")
    public Result<Map<String, Object>> adminGetLocations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        List<Location> all = keyword != null && !keyword.isEmpty()
                ? mapService.searchLocations(keyword)
                : mapService.getAllLocations();

        int total = all.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Location> pageList = fromIndex < total ? all.subList(fromIndex, toIndex) : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }
}
