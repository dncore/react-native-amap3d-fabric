package qiuxiang.amap3d.map_view

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MyLocationStyle
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import qiuxiang.amap3d.getFloat
import qiuxiang.amap3d.toJson
import qiuxiang.amap3d.toLatLng
import qiuxiang.amap3d.toLatLngList
import qiuxiang.amap3d.toPoint

@SuppressLint("ViewConstructor")
class MapView(context: ThemedReactContext) : TextureMapView(context) {
  @Suppress("Deprecation")
  private val eventEmitter =
    context.getJSModule(com.facebook.react.uimanager.events.RCTEventEmitter::class.java)
  private val markerMap = HashMap<String, qiuxiang.amap3d.map_view.Marker>()
  private val polylineMap = HashMap<String, Polyline>()
  private var initialCameraPosition: ReadableMap? = null
  private var locationStyle: MyLocationStyle

  init {
    super.onCreate(null)

    locationStyle = MyLocationStyle()
    locationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
    map.myLocationStyle = locationStyle

    map.setOnMapLoadedListener { emit(id, "onLoad"); tryDrawRoute() }
    map.setOnMapClickListener { latLng -> emit(id, "onPress", latLng.toJson()) }
    map.setOnPOIClickListener { poi -> emit(id, "onPressPoi", poi.toJson()) }
    map.setOnMapLongClickListener { latLng -> emit(id, "onLongPress", latLng.toJson()) }
    map.setOnPolylineClickListener { polyline -> emit(polylineMap[polyline.id]?.id, "onPress") }

    map.setOnMarkerClickListener { marker ->
      markerMap[marker.id]?.let { emit(it.id, "onPress") }
      true
    }

    map.setOnMarkerDragListener(object : AMap.OnMarkerDragListener {
      override fun onMarkerDragStart(marker: Marker) {
        emit(markerMap[marker.id]?.id, "onDragStart")
      }

      override fun onMarkerDrag(marker: Marker) {
        emit(markerMap[marker.id]?.id, "onDrag")
      }

      override fun onMarkerDragEnd(marker: Marker) {
        emit(markerMap[marker.id]?.id, "onDragEnd", marker.position.toJson())
      }
    })

    map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
      override fun onCameraChangeFinish(position: CameraPosition) {
        emit(id, "onCameraIdle", Arguments.createMap().apply {
          putMap("cameraPosition", position.toJson())
          putMap("latLngBounds", map.projection.visibleRegion.latLngBounds.toJson())
        })
      }

      override fun onCameraChange(position: CameraPosition) {
        emit(id, "onCameraMove", Arguments.createMap().apply {
          putMap("cameraPosition", position.toJson())
          putMap("latLngBounds", map.projection.visibleRegion.latLngBounds.toJson())
        })
      }
    })

    map.setOnMultiPointClickListener { item ->
      item.customerId.split("_").let {
        emit(
          it[0].toInt(),
          "onPress",
          Arguments.createMap().apply { putInt("index", it[1].toInt()) },
        )
      }
      false
    }

    map.setOnMyLocationChangeListener {
      if (it.time > 0) {
        emit(id, "onLocation", it.toJson())
      }
    }

    // Prevent parent ScrollView from intercepting map gestures.
    // OnTouchListener fires before onTouchEvent; returning false lets
    // TextureMapView's own touch handling proceed normally.
    setOnTouchListener { _, event ->
      when (event.action and MotionEvent.ACTION_MASK) {
        MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
          parent?.requestDisallowInterceptTouchEvent(false)
      }
      false
    }
  }

  fun emit(id: Int?, event: String, data: WritableMap = Arguments.createMap()) {
    @Suppress("Deprecation")
    id?.let { eventEmitter.receiveEvent(it, event, data) }
  }

  fun add(child: View) {
    if (child is Overlay) {
      child.add(map)
      if (child is qiuxiang.amap3d.map_view.Marker) {
        markerMap[child.marker?.id!!] = child
      }
      if (child is Polyline) {
        polylineMap[child.polyline?.id!!] = child
      }
    }
  }

  fun remove(child: View) {
    if (child is Overlay) {
      child.remove()
      if (child is qiuxiang.amap3d.map_view.Marker) {
        markerMap.remove(child.marker?.id)
      }
      if (child is Polyline) {
        polylineMap.remove(child.polyline?.id)
      }
    }
  }

  private val animateCallback = object : AMap.CancelableCallback {
    override fun onCancel() {}
    override fun onFinish() {}
  }

  fun moveCamera(args: ReadableArray?) {
    val current = map.cameraPosition
    val position = args?.getMap(0)!!
    val target = position.getMap("target")?.toLatLng() ?: current.target
    val zoom = position.getFloat("zoom") ?: current.zoom
    val tilt = position.getFloat("tilt") ?: current.tilt
    val bearing = position.getFloat("bearing") ?: current.bearing
    val cameraUpdate = CameraUpdateFactory.newCameraPosition(
      CameraPosition(target, zoom, tilt, bearing)
    )
    map.animateCamera(cameraUpdate, args.getInt(1).toLong(), animateCallback)
  }

  fun setInitialCameraPosition(position: ReadableMap) {
    if (initialCameraPosition == null) {
      initialCameraPosition = position
      moveCamera(Arguments.createArray().apply {
        pushMap(Arguments.createMap().apply { merge(position) })
        pushInt(0)
      })
    }
  }



  private var pendingRouteJson: String? = null

  fun setRenderFps(fps: Int) {
    map.setRenderFps(fps)
  }

  fun setLogoEnabled(enabled: Boolean) {
    if (!enabled) {
      // Push the AMap logo off-screen using the official UiSettings margin APIs.
      // setLogoCenter may be clamped; negative margins are more reliable.
      // Do NOT call showMapText(false) — that hides all street/place labels.
      map.uiSettings.setLogoLeftMargin(-2000)
      map.uiSettings.setLogoBottomMargin(-2000)
    }
  }

  fun setRouteData(json: String) {
    pendingRouteJson = json
    tryDrawRoute()
  }

  private fun tryDrawRoute() {
    val json = pendingRouteJson ?: return
    try {
      val arr = com.facebook.react.bridge.Arguments.createArray()
      val parsed = org.json.JSONArray(json)
      for (i in 0 until parsed.length()) {
        val pt = parsed.getJSONObject(i)
        arr.pushMap(com.facebook.react.bridge.Arguments.createMap().apply {
          putDouble("latitude", pt.getDouble("latitude"))
          putDouble("longitude", pt.getDouble("longitude"))
        })
      }
      val points = arr.toLatLngList()
      if (points.isEmpty()) return
      val opts = com.amap.api.maps.model.PolylineOptions()
        .addAll(points)
        .color(0xff1F78FF.toInt())
        .width(12f)
        .geodesic(true)
      map.addPolyline(opts)
      pendingRouteJson = null
    } catch(e: Exception) {
      e.printStackTrace()
    }
  }  fun call(args: ReadableArray?) {
    val id = args?.getDouble(0)!!
    when (args.getString(1)) {
      "getLatLng" -> callback(
        id,
        // @todo 暂时兼容 0.63
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        map.projection.fromScreenLocation(args.getMap(2)!!.toPoint()).toJson()
      )
    }
  }

  private fun callback(id: Double, data: Any) {
    emit(this.id, "onCallback", Arguments.createMap().apply {
      putDouble("id", id)
      when (data) {
        is WritableMap -> putMap("data", data)
      }
    })
  }
}
