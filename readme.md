# react-native-amap3d-fabric

> React Native 高德地图组件，支持 **Fabric (newArchEnabled=true)**。
>
> **[react-native-amap3d](https://github.com/qiuxiang/react-native-amap3d) 的 Fabric 兼容 Fork，原作者 [7c00](https://github.com/qiuxiang)。**

[![version-badge]][npm]
[![](https://img.shields.io/badge/arch-fabric-blue)]()
[![license](https://img.shields.io/npm/l/react-native-amap3d-fabric.svg)](./license)

## 为什么有这个 Fork

上游 [react-native-amap3d](https://github.com/qiuxiang/react-native-amap3d) 已超过 3 年未更新，不支持 React Native 新架构 (Fabric)。当 `newArchEnabled=true` 时，Fabric interop 层为每个 `ViewManager` 生成 view config，调用 `getExportedCustomDirectEventTypeConstants()`。

`ViewGroupManager` 默认返回 `null`，Fabric 解析时抛出：

```
UnexpectedNativeTypeException: expected Array, got a null
```

应用在 JS bundle 加载阶段直接崩溃白屏。

本 fork 以最小改动让 react-native-amap3d 在 React Native 0.71+ Fabric 环境下正常运行。

## Fork 修改清单

### Fabric 兼容 — Android 7 个 ViewManager

7 个 ViewManager 覆写 `getExportedCustomDirectEventTypeConstants()`，返回 `emptyMap()`：

| 文件 | 类 | 对应组件 |
|------|-----|----------|
| `MapViewManager.kt` | `ViewGroupManager<MapView>` | AMapView |
| `MarkerManager.kt` | `ViewGroupManager<Marker>` | AMapMarker |
| `PolylineManager.kt` | `ViewGroupManager<Polyline>` | AMapPolyline |
| `PolygonManager.kt` | `SimpleViewManager<Polygon>` | AMapPolygon |
| `CircleManager.kt` | `SimpleViewManager<Circle>` | AMapCircle |
| `HeatMapManager.kt` | `SimpleViewManager<HeatMap>` | AMapHeatMap |
| `MultiPointManager.kt` | `SimpleViewManager<MultiPoint>` | AMapMultiPoint |

### 隐私合规初始化

AMap SDK 9.x 要求在任何 API 调用前完成隐私合规设置。在 `MapViewManager.createViewInstance()` 中内联调用：

```kotlin
MapsInitializer.updatePrivacyAgree(reactContext, true)
MapsInitializer.updatePrivacyShow(reactContext, true, true)
AMapLocationClient.updatePrivacyAgree(reactContext, true)
AMapLocationClient.updatePrivacyShow(reactContext, true, true)
```

### 手势与 ScrollView 冲突修复

Android (`MapView.kt`) 通过 `setOnTouchListener` 在 `ACTION_DOWN` 时请求父容器不要拦截触摸事件。
iOS (`MapViewManager.swift`) 在 `didMoveToSuperview()` 中遍历视图树查找父级 `UIScrollView`，将地图手势设为父级 pan gesture 的依赖。

### 新增属性

| 属性 | 平台 | 说明 |
|------|------|------|
| `logoEnabled` | Android + iOS | 隐藏高德 logo 水印 |
| `renderFps` | Android + iOS | 设置地图渲染帧率（30/60） |
| `routeData` | Android | JSON 坐标数组，自动绘制 geodesic 折线 |

---

## 安装

```bash
npm install react-native-amap3d-fabric
```

### Android

`android/app/src/main/AndroidManifest.xml` 添加：

```xml
<application>
  <meta-data
    android:name="amap_key"
    android:value="YOUR_AMAP_KEY" />
</application>
```

### iOS

```ruby
pod 'react-native-amap3d-fabric', :path => '../node_modules/react-native-amap3d-fabric'
```

## 用法

与原版 react-native-amap3d API 完全兼容，import 路径改为 `react-native-amap3d-fabric`：

```tsx
import { AMapView, MapType } from "react-native-amap3d-fabric";

<AMapView
  mapType={MapType.Standard}
  initialCameraPosition={{
    target: { latitude: 39.91095, longitude: 116.37296 },
    zoom: 8,
  }}
  logoEnabled={false}
  renderFps={60}
/>;
```

更多示例参考原项目 [example-app](https://github.com/qiuxiang/react-native-amap3d/tree/master/example-app) 及 [接口文档](https://qiuxiang.github.io/react-native-amap3d/api/)。

## 从原版迁移

1. `package.json` 中 `react-native-amap3d` → `react-native-amap3d-fabric`
2. import 路径 `'react-native-amap3d'` → `'react-native-amap3d-fabric'`
3. `android/settings.gradle` 中无需手动 include（autolinking 自动处理）

## 兼容性

| React Native | AMap SDK (Android) | AMap SDK (iOS) | 状态 |
|--------------|-------------------|----------------|------|
| 0.71+ (Fabric) | 9.6.0 | 9.6.2 | ✅ 验证通过 |
| 0.71+ (Paper) | 9.6.0 | 9.6.2 | 未测试 |

## 常见问题

- 尽量使用真实设备测试，模拟器可能存在 GPU 相关闪退
- onLocation 无数据通常是因为 key 配置错误或未申请定位权限
- API Key 可通过 AndroidManifest `amap_key` meta-data 配置，或调用 `AMapSdk.init(apiKey)` 设置

## 许可证

MIT License — 原始工作 Copyright (c) 2023 7c00，Fork 修改 Copyright (c) 2026 Contributors。

[npm]: https://www.npmjs.com/package/react-native-amap3d-fabric
[version-badge]: https://img.shields.io/npm/v/react-native-amap3d-fabric.svg
