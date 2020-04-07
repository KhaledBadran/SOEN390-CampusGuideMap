package com.droidhats.campuscompass.models

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * A Model for the map.
 * This class has the objective of initializing the map, attaching listeners to it, and drawing the
 * polygons and markers for the buildings on the map. It follows a singelton design pattern because
 * we only need to initialize the map once.
 *
 * @constructor Creates an initialized GoogleMap object.
 * @param googleMap: A GoogleMap Object will be used to initialize the map.
 * @param buildings: A list of all concordia buildings that is used to draw map polygons and markers.
 */
class Map(
    var googleMap: GoogleMap,
    private var buildings: List<Building>
) {
    companion object {
        private const val MAP_PADDING_TOP = 200
        private const val MAP_PADDING_RIGHT = 15
    }
    /**
     * Initializes the map, and attaches listeners to it.
     */
    init {
        googleMap.clear()

        //updating map type we can choose between  4 types : MAP_TYPE_NORMAL, MAP_TYPE_SATELLITE, MAP_TYPE_TERRAIN, MAP_TYPE_HYBRID
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        //enable the zoom controls on the map and declare MainActivity as the callback triggered when the user clicks a marker on this map
        googleMap.uiSettings.isZoomControlsEnabled = true

        //enable indoor level picker
        googleMap.isIndoorEnabled = true
        googleMap.uiSettings.isIndoorLevelPickerEnabled = true

        //Current Location Icon has been adjusted to be at the bottom right sid eof the search bar.
        googleMap.setPadding(0, MAP_PADDING_TOP, MAP_PADDING_RIGHT, 0)

        // Draw the buildings polygons and markers
        for (building in buildings) {
            drawBuildingPolygon(building)
        }
        setBuildingMarker()
        addShuttleStopMarkers()

    }

    private fun addShuttleStopMarkers() {
        googleMap.addMarker(
            MarkerOptions().position(Campus.SGW_SHUTTLE_STOP)
                .title("Shuttle Bus Stop"))
        googleMap.addMarker(
            MarkerOptions().position(Campus.LOY_SHUTTLE_STOP)
                .title("Shuttle Bus Stop"))
    }

    /**
     * Draws the polygon for a single building on the map
     */
    private fun drawBuildingPolygon(building: Building){
        googleMap.addPolygon(building.getPolygonOptions()).tag = building.name
        val polygon = googleMap.addPolygon(building.getPolygonOptions())
        building.setPolygon(polygon)
    }

    /**
     * Draws the marker for a single building on the map
     */
    private fun setBuildingMarker() {
        for (building in buildings) {
            if (building.hasCenterLocation()){
                val marker: Marker = googleMap.addMarker(building.getMarkerOptions())
                building.setMarker(marker)
                // Set the marker to become the new bitmap rather than the conventional map pin
                building.getMarker()
                    .setIcon(BitmapDescriptorFactory.fromResource(building.getMarkerResId()))
            }
        }
    }
}