package com.droidhats.campuscompass.views

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import android.widget.Switch
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.droidhats.campuscompass.MainActivity
import com.droidhats.campuscompass.R
import com.droidhats.campuscompass.adapters.FavoritesAdapter
import com.droidhats.campuscompass.adapters.SearchAdapter
import com.droidhats.campuscompass.helpers.Subject
import com.droidhats.campuscompass.models.Building
import com.droidhats.campuscompass.models.OutdoorNavigationRoute
import com.droidhats.campuscompass.models.GooglePlace
import com.droidhats.campuscompass.models.CalendarEvent
import com.droidhats.campuscompass.models.IndoorLocation
import com.droidhats.campuscompass.models.FavoritePlace
import com.droidhats.campuscompass.models.ExplorePlace
import com.droidhats.campuscompass.roomdb.FavoritesDatabase
import com.droidhats.campuscompass.viewmodels.MapViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mancj.materialsearchbar.MaterialSearchBar
import kotlinx.android.synthetic.main.bottom_sheet_layout.bottom_sheet
import kotlinx.android.synthetic.main.instructions_sheet_layout.prevArrow
import kotlinx.android.synthetic.main.instructions_sheet_layout.nextArrow
import kotlinx.android.synthetic.main.instructions_sheet_layout.doneButtonMap
import kotlinx.android.synthetic.main.instructions_sheet_layout.arrayInstruction
import kotlinx.android.synthetic.main.search_bar_layout.buttonResumeNavigation
import kotlinx.android.synthetic.main.search_bar_layout.toggleButton
import kotlinx.android.synthetic.main.search_bar_layout.mapFragSearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan
import com.droidhats.campuscompass.helpers.Observer as ModifiedObserver
import com.droidhats.campuscompass.models.Map as MapModel

/**
 * A View Fragment for the map.
 * It displays all the UI components of the map and dynamically interacts with the user input.
 */
class MapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener,
    GoogleMap.OnPolygonClickListener, CalendarFragment.OnCalendarEventClickListener, SearchAdapter.OnSearchResultClickListener, ExploreCategoryFragment.OnExplorePlaceClickListener, OnCameraIdleListener,
    FavoritesAdapter.OnFavoriteClickListener, FloorFragment.OnFloorFragmentBackClicked, Subject {

    private var mapModel: MapModel? = null
    private var map: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private val observerList = mutableListOf<ModifiedObserver?>()
    private val currentNavigationPath = arrayListOf<Polyline>()
    private val preferenceOff = ArrayList<String>()
    private var locationUpdateState = false

    companion object {
        private const val REQUEST_CHECK_SETTINGS = 2
        private const val MAP_PADDING_TOP = 200
        private const val MAP_PADDING_RIGHT = 15
        private var trackerSteps = 0
        private var currentOutdoorNavigationRoute : OutdoorNavigationRoute? = null
    }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var viewModel: MapViewModel

    private lateinit var root : View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        root = inflater.inflate(R.layout.map_fragment, container, false)
        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MapViewModel::class.java)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity as Activity)

        //update lastLocation with the new location and update the map with the new location coordinates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                lastLocation = p0.lastLocation
            }
        }

        //Event click callbacks
        CalendarFragment.onCalendarEventClickListener = this
        SearchFragment.onSearchResultClickListener = this
        MyPlacesFragment.onFavoriteClickListener = this
        ExploreCategoryFragment.onExplorePlaceClickListener = this
        FloorFragment.OnFloorFragmentBackClicked = this

        createLocationRequest()
        initBottomSheetBehavior()
        initSearchBar()
        handleCampusSwitch()
        preferenceOff.clear()
        checkSettingOptions()
    }

    /**
     * Initializes the map and adds markers or lines and attaches listeners
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     *
     * @param googleMap a necessary google map object on which we add markers and attach listeners.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        // Get the map from the viewModel.
        mapModel = viewModel.getMapModel(googleMap, this, this, this, this.activity as MainActivity)
        map = mapModel?.googleMap

        //Add custom style to map
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    context, R.raw.map_style))
            if (!success) {
                Log.e("MapStyle", "Style parsing failed.")
            }
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.e("MapStyle", "Can't find style. Error: ", e)
        }

        // Move camera to SGW
        moveTo(viewModel.getCampuses()[0].getLocation(), 16f)
        toggleButton.setChecked(false)

        map!!.setOnMapClickListener {
            //Dismiss the bottom sheet when clicking anywhere on the map
            dismissBottomSheet()
        }

        if (currentOutdoorNavigationRoute != null) drawPathPolyline(currentOutdoorNavigationRoute!!.polyLinePath)

        attachBuildingObservers()
        observeNavigation()
        setNavigationButtons()
        attach(mapModel)
    }


    /**
     * Handle coming coming back to the map fragment from the floor fragment.
     * This method pops the stack back to the map fragment and resets the current navigation path
     */
    override fun onFloorFragmentBackClicked(){
        findNavController().popBackStack(R.id.map_fragment, false)
        currentOutdoorNavigationRoute = null
    }

    /**
     * Attaches all Concordia's buildings that have initial markers to the map so that they are
     * observing the changes in the map zoom level.
     */
    private fun attachBuildingObservers(){
        // Attach all observer buildings with initial markers
        for (building in viewModel.getBuildings()) {
            if (!observerList.contains(building) && building.hasCenterLocation()) {
                attach(building)
            }
        }
    }

    /**
     * Detaches all Concordia's buildings that were observing the map from the observer list.
     */
    private fun detachBuildingObservers(){
        // Detach all observer buildings with initial markers
        for (building in viewModel.getBuildings()) {
            if (observerList.contains(building) && building.hasCenterLocation()) {
                detach(building)
            }
        }
    }

    /**
     * Allows the map to observe the navigation route using the viewModel.
     * Different actions are taken depending on the observed navigation route. The handled cases
     * are related to whether the outdoor and indoor navigation routes have been initialized.
     */
    private fun observeNavigation() {
        viewModel.getNavigationRoute().observe(viewLifecycleOwner, Observer {
            // The observer's OnChange is called when the Fragment gets pushed back even when the object didn't change
            // Remove the condition check to keep the path drawn on the screen even after changing activities
            // If the condition is removed though, camera movement must be handled properly not to override other movement
            if ( it != null && it != currentOutdoorNavigationRoute ) {
                if (it is OutdoorNavigationRoute) {
                    requireActivity().onBackPressedDispatcher.addCallback(this) {
                        viewModel.navigationRepository.stepBack()
                    }
                    currentOutdoorNavigationRoute = it
                    drawPathPolyline(it.polyLinePath)
                    showInstructions(it.instructions, it.instructionsCoordinates)
                    Handler().postDelayed({
                        moveTo(it.origin!!.getLocation(), 19.0f)
                    }, 100)
                } else {
                    findNavController().navigate(R.id.floor_fragment)
                }
            } else if (it == null) {
                cancelNavigation()
                requireActivity().onBackPressedDispatcher.addCallback {
                    if (findNavController().currentDestination?.id == R.id.map_fragment)
                        requireActivity().finishAffinity()
                    else
                        findNavController().navigateUp()
                }
            }
        })
        trackerSteps = 0

        val doneButton: Button = requireActivity().findViewById(R.id.doneButtonMap)

        // The done button ("I've arrived") will initially be hidden
        doneButton.visibility = View.GONE

    }

    /**
     * This function sets the navigation button on the instruction sheet that appears when there is
     * an outdoor navigation. It will also toggle the instruction view depending on whether the
     * current outdoor navigation is set to null.
     */
    private fun setNavigationButtons() {
        val buttonMinimizeInstructions : ImageButton = requireActivity().findViewById(R.id.buttonMinimizeInstructions)
        buttonMinimizeInstructions.setOnClickListener{
            toggleInstructionsView(false)
        }

        val buttonResumeNavigation : Button = requireActivity().findViewById(R.id.buttonResumeNavigation)
        buttonResumeNavigation.setOnClickListener{
            dismissBottomSheet()
            toggleInstructionsView(true)
        }

        // The close button in the instruction sheet
        val buttonCloseInstructions : ImageButton = requireActivity().findViewById(R.id.buttonCloseInstructions)
        buttonCloseInstructions.setOnClickListener{
            cancelNavigation()
            viewModel.navigationRepository.cancelNavigation()
        }

        if(currentOutdoorNavigationRoute != null) {
            showInstructions(currentOutdoorNavigationRoute!!.instructions, currentOutdoorNavigationRoute!!.instructionsCoordinates)
            toggleInstructionsView(true)
        }
    }

    /**
     * This will cancel the current navigation by clearing the current drawn path, resetting the
     * current outdoor navigation route and hiding the instruction sheet.
     */
    private fun cancelNavigation() {
        clearNavigationPath()
        currentOutdoorNavigationRoute = null
        toggleInstructionsView(false)

        // When the close button is clicked, we make the campus toggle button visible again
        // We also add a listener to handle the campus switching
        toggleButton.visibility = View.VISIBLE
        handleCampusSwitch()
    }

    /**
     * Creates a request for the location. This method will first set up a location request and then
     * send it and handle the returned value. The location request is an API call to Google services
     */
    private fun createLocationRequest() {
        // 1 create an instance of LocationRequest, add it to an instance of LocationSettingsRequest.Builder and retrieve and handle any changes to be made based on the current state of the user’s location settings.
        locationRequest = LocationRequest()
        // 2 specifies the rate at which your app will like to receive updates.
        locationRequest.interval = 10000
        // 3 specifies the fastest rate at which the app can handle updates. Setting the fastestInterval rate places a limit on how fast updates will be sent to your app.
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        // 4 check location settings before asking for location updates
        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        // 5 A task success means all is well and you can go ahead and initiate a location request
        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }

        task.addOnFailureListener { e ->
            // 6  A task failure means the location settings have some issues which can be fixed. This could be as a result of the user’s location settings turned off
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(
                        activity,
                        REQUEST_CHECK_SETTINGS
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    /**
     * Get real time updates of current location.
     */
    private fun startLocationUpdates() {
        //requests for location updates.
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null //* Looper *//*
        )
    }

    /**
     *  Override AppCompatActivity’s onActivityResult() method and start the update request if it
     *  has a RESULT_OK result for a REQUEST_CHECK_SETTINGS request.
     *
     *  @param requestCode checks if the user has allowed access to current location
     *  @param resultCode checks the result of the request check
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
        }
    }

    /**
     * Override onPause() to stop location update request
     */
    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        dismissBottomSheet()
    }

    /**
     * Override onResume() to restart the location update request.
     */
    override fun onResume() {
        super.onResume()
        if (!locationUpdateState) {
            startLocationUpdates()
        }
    }

    /**
     * Override onDestroy() to detach all of concordia's buildings from the observer list of the map.
     * It will then detach the mapModel from the observers.
     */
    override fun onDestroy(){
        super.onDestroy()
        detachBuildingObservers()
        detach(mapModel)
    }

    /**
     * Implements methods of interface GoogleMap.GoogleMap.OnPolygonClickListener
     * This will be used to listen to the clicks on the polygons that are set for the map. Each of
     * the drawn buildings on the map is represented by a polygon!
     *
     * @param p Is the building polygon that was clicked
     */
    override fun onPolygonClick(p: Polygon) {
        val selectedBuilding : Building = viewModel.findBuildingByPolygonTag(p.tag.toString())
            ?: return

        handleBuildingClick(selectedBuilding)
    }


    /**
     * implements methods of interface GoogleMap.OnMarkerClickListener
     * This will be used to listen to the clicks on the markers  that are set for the map. Some of
     * the drawn buildings on the map have a corresponding markers. We can handle the building click
     * by behaviour by listening to clicks on the buildings markers.
     *
     * @param marker Is the building marker that was clicked
     */
    override fun onMarkerClick(marker: Marker?): Boolean {
        val selectedBuilding: Building? = viewModel.findBuildingByMarkerTitle(marker)
        //There is a building associated with the marker that was clicked
        if (selectedBuilding != null) {
            handleBuildingClick(selectedBuilding)
            return true //disable ability to tap the marker to avoid default behavior
        }
        return false
    }

    /**
     * Handles the map behaviour when a click on one of the drawn buildings is detected.
     * This will expand the bottom sheet that is populated by the building information.
     * This will also initialize the buttons that appear on the buildings info sheet and set up
     * their on click listeners.
     *
     * @param building Is the building object that was clicked
     */
    private fun handleBuildingClick(building: Building) {
        expandBottomSheet()
        updateAdditionalInfoBottomSheet(building.getPolygon())

        val directionsButton: Button = requireActivity().findViewById(R.id.bottom_sheet_directions_button)
        directionsButton.setOnClickListener {
            dismissBottomSheet()
            val bundle = Bundle()
            bundle.putParcelable("destBuilding", building)
            findNavController().navigate(R.id.search_fragment, bundle)
        }

        val indoorMapsButton: Button = requireActivity().findViewById(R.id.bottom_sheet_floor_map_button)
        indoorMapsButton.setOnClickListener {
            if (building.getIndoorInfo().second.isEmpty()) {
                Toast.makeText(
                    context,
                    "This building doesn't have a floor map",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val bundle: Bundle = Bundle()
                bundle.putParcelable("building", building)
                findNavController().navigate(R.id.floor_fragment, bundle)
            }
        }
    }

    /**
     * Handle the switching views between the two campuses.
     * This method will move the focus of the map camera to the designated lat and lang coordiantes
     * of each of concordia's campuses upon a click on the toggle button.
     */
    private fun handleCampusSwitch() {
        var campusView: LatLng

        //Setting Toggle button listener
        toggleButton.setOnCheckedChangeListener { _, onSwitch ->
            if (onSwitch) {
                campusView = LatLng(45.458220, -73.639702)
                moveTo(campusView, 16f)
            } else {
                campusView = LatLng(45.495784, -73.577197)
                moveTo(campusView, 16f)
            }
            dismissBottomSheet()
        }
    }

    /**
     * Shows the outdoor navigation route instructions.
     * This method will handle the displaying of the instruction sheet, and setting and initializing
     * the list of instructions for the navigation route. It will also handle the buttons for clicking
     * on the next and previous buttons on the instructions. This will also move the camera at each
     * instruction to allow the user to dynamically navigate on the map.
     *
     * @param instructions is a list of instructions for the navigation route
     * @param instructionsCoordinates is the coordinates the corresponds for each of the instructions
     * in the instructions list. Its used to move the camera focus to the coordinates of the instruction.
     */
    private fun showInstructions(instructions: ArrayList<String>, instructionsCoordinates: ArrayList<LatLng>){
        toggleButton.visibility = View.GONE
        toggleInstructionsView(true)
        arrayInstruction.text = Html.fromHtml(instructions[0]).toString()
        val doneButton: Button = requireActivity().findViewById(R.id.doneButtonMap)
        if (viewModel.navigationRepository.isLastStep()) {
            doneButtonMap.text = "Finish!"
            doneButton.setOnClickListener {
                viewModel.navigationRepository.cancelNavigation()
            }
        } else {
            doneButtonMap.text = "Take me inside!"
            doneButton.setOnClickListener {
                viewModel.navigationRepository.consumeNavigationHandler()
            }
        }

        setNavigationNextArrow(instructions, instructionsCoordinates)
        setNavigationBackArrow(instructions, instructionsCoordinates)
    }

    /**
     * Sets the functionality to the navigation arrows when navigating
     * @param instructions set
     * @param instructionsCoordinates
     */
    private fun setNavigationNextArrow(instructions: ArrayList<String>, instructionsCoordinates: ArrayList<LatLng>) {
        nextArrow.setOnClickListener{
            prevArrow.visibility = View.VISIBLE
            trackerSteps++
            arrayInstruction.text = Html.fromHtml(instructions[trackerSteps]).toString()
            if (trackerSteps < instructions.size -1) {
                val bearingValue = getBearing(
                    instructionsCoordinates[trackerSteps].latitude,
                    instructionsCoordinates[trackerSteps].longitude,
                    instructionsCoordinates[trackerSteps+1].latitude,
                    instructionsCoordinates[trackerSteps+1].longitude
                )
                if (!bearingValue.isNaN()) {
                    val cameraPosition: CameraPosition = CameraPosition.Builder().target(
                        LatLng(
                            instructionsCoordinates[trackerSteps].latitude,
                            instructionsCoordinates[trackerSteps].longitude)
                    ).zoom(20.0F).bearing(bearingValue).tilt(0F).build()
                    map!!.animateCamera(
                        CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null)
                }
            } else if (trackerSteps == instructions.size - 1) {
                nextArrow.visibility = View.INVISIBLE
                doneButtonMap.visibility = View.VISIBLE
                moveTo(instructionsCoordinates[instructions.size - 1], 20.0F)
            }
        }
    }

    private fun setNavigationBackArrow(instructions: ArrayList<String>, instructionsCoordinates: ArrayList<LatLng>) {
        prevArrow.setOnClickListener{
            nextArrow.visibility = View.VISIBLE
            doneButtonMap.visibility = View.GONE
            trackerSteps--
            arrayInstruction.text = Html.fromHtml(instructions[trackerSteps]).toString()
            if (trackerSteps != 0) {
                if (trackerSteps < instructions.size -1) {
                    val bearingValue = getBearing(
                        instructionsCoordinates[trackerSteps].latitude,
                        instructionsCoordinates[trackerSteps].longitude,
                        instructionsCoordinates[trackerSteps + 1].latitude,
                        instructionsCoordinates[trackerSteps + 1].longitude
                    )
                    if (!bearingValue.isNaN()) {
                        val cameraPosition: CameraPosition = CameraPosition.Builder().target(
                            LatLng(
                                instructionsCoordinates[trackerSteps].latitude,
                                instructionsCoordinates[trackerSteps].longitude)
                        ).zoom(20.0F).bearing(bearingValue).tilt(0F).build()
                        map!!.animateCamera(
                            CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null)
                    }
                }
            }
            else {
                prevArrow.visibility = View.INVISIBLE
                moveTo(instructionsCoordinates[0], 20.0F)
            }
        }
    }

    /**
     * Used to routate the camera on the map depending on where the user is looking at.
     * It will allow the camera to return back the default setting of pointing North.
     *
     * @param startLat Latitude coordinate for the start point
     * @param startLong Longitude coordinate for the start point
     * @param endLat Latitude coordinate for the end point
     * @param endLong Longitude coordinate for the end point
     */
    private fun getBearing(startLat: Double, startLong: Double, endLat: Double, endLong: Double): Float {
        val lat = abs(startLat - endLat)
        val lng = abs(startLong - endLong)

        if (startLat < endLat && startLong < endLong) {
            return Math.toDegrees(atan(lng / lat)).toFloat()
        } else if (startLat >= endLat && startLong < endLong) {
            return (90 - Math.toDegrees(atan(lng / lat)) + 90).toFloat()
        } else if (startLat >= endLat && startLong >= endLong) {
            return (Math.toDegrees(atan(lng / lat)) + 180).toFloat()
        } else if (startLat < endLat && startLong >= endLong) {
            return (90 - Math.toDegrees(atan(lng / lat)) + 270).toFloat()
        }
        return (-1).toFloat()
    }

    /**
     * Toggles the instruction sheet for the outdoor navigation instructions on and off.
     * This will check if the instruction sheet if visible and hide it in that case. If the
     * instruction sheet, a resume navigation button is displayed.
     *
     * @param isVisible checks if the instruction sheet is currently visible
     */
    private fun toggleInstructionsView(isVisible: Boolean){
        val instructionsView : CardView = requireActivity().findViewById(R.id.instructionLayout)
        if(isVisible){
            togglePlaceCard(false)
            instructionsView.visibility = View.VISIBLE
            buttonResumeNavigation.visibility = View.INVISIBLE
            map?.setPadding(0, MAP_PADDING_TOP, MAP_PADDING_RIGHT, instructionsView.height+20)
        }
        else{
            instructionsView.visibility = View.INVISIBLE

            map?.setPadding(0, MAP_PADDING_TOP, MAP_PADDING_RIGHT, 0)
            if(currentOutdoorNavigationRoute != null)
                buttonResumeNavigation.visibility = View.VISIBLE
        }
    }

    /**
     * This method will draw the polylines for the outdoor navigation route on the map.
     *
     * @param path A list of LatLang coordinates that ccorrespondto the outdoor navigation route's
     * polyline positions on the map.
     */
    private fun drawPathPolyline(path : MutableList<List<LatLng>>) {
        clearNavigationPath()  //Clear existing path to show only one path at a time
        for (i in 0 until path.size) {
            val polyline= map!!.addPolyline(context?.let { ContextCompat.getColor(it, R.color.colorPrimaryDark) }?.let {
                PolylineOptions().addAll(path[i]).width(10F).color(it)
            })
            currentNavigationPath.add(polyline)
        }
    }
    
    private fun clearNavigationPath() {
        for ( polyline in currentNavigationPath){
            polyline.remove()
        }
        currentNavigationPath.clear()
    }

    private fun initSearchBar() {
        mapFragSearchBar.setOnSearchActionListener(object : MaterialSearchBar.OnSearchActionListener{

            override fun onButtonClicked(buttonCode: Int) {
                when(buttonCode) {
                    //Open the Nav Bar
                    MaterialSearchBar.BUTTON_NAVIGATION -> requireActivity().
                        findViewById<DrawerLayout>(R.id.drawer_layout).openDrawer(GravityCompat.START)
                }
            }
            override fun onSearchStateChanged(enabled: Boolean) {
                if (enabled) {
                    dismissBottomSheet()
                    findNavController().navigate(R.id.search_fragment)
                    mapFragSearchBar.closeSearch()
                }
            }
            override fun onSearchConfirmed(text: CharSequence?) {
                // not being used
            }
        })
    }

    private fun initBottomSheetBehavior() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
        bottomSheetBehavior.setBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // React to state change
                // The following code can be used if we want to do certain actions related
                // to the change of state of the bottom sheet
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Adjusting the google zoom buttons to stay on top of the bottom sheet
                //Multiply the bottom sheet height by the offset to get the effect of them being anchored to the top of the sheet
                map!!.setPadding(0, MAP_PADDING_TOP, MAP_PADDING_RIGHT, (slideOffset * root.findViewById<NestedScrollView>(R.id.bottom_sheet).height).toInt())
            }
        })
    }

    private fun dismissBottomSheet() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun expandBottomSheet() {
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
            togglePlaceCard(false)
            toggleInstructionsView(false)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }

    private fun updateAdditionalInfoBottomSheet(p: Polygon) {
        // Update the bottom sheet with building information
        val buildingName: TextView = requireActivity().findViewById(R.id.bottom_sheet_building_name)
        val buildingAddress: TextView =
            requireActivity().findViewById(R.id.bottom_sheet_building_address)
        val buildingOpenHours: TextView = requireActivity().findViewById(R.id.bottom_sheet_open_hours)
        val buildingServices: TextView = requireActivity().findViewById(R.id.bottom_sheet_services)
        val buildingDepartments: TextView =
            requireActivity().findViewById(R.id.bottom_sheet_departments)
        val buildingImage: ImageView = requireActivity().findViewById(R.id.building_image)

        for (building in viewModel.getBuildings()) {
            if (building.getPolygon().tag == p.tag) {
                buildingName.text = p.tag.toString()
                buildingAddress.text = building.getAddress()
                buildingOpenHours.text = building.getOpenHours()
                buildingServices.text = building.getServices()
                buildingDepartments.text = building.getDepartments()
                buildingImage.setImageResource(building.getImageResId())
            }
        }
    }

    override fun onCalendarEventClick(item: CalendarEvent?) {
        // add no functionality for this overridden function
    }

    override fun onSearchResultClickListener(item: com.droidhats.campuscompass.models.Location?) {
        var isCampusBuilding = false
        currentOutdoorNavigationRoute = null
        viewModel.navigationRepository.cancelNavigation()
        if (item is GooglePlace) {
            findNavController().popBackStack(R.id.map_fragment, false)
            GlobalScope.launch(Dispatchers.Main) {
                for (building in viewModel.getBuildings())
                    if (building.getPlaceId() == item.placeID) { //Check if location is a concordia building
                        isCampusBuilding = true
                        handleBuildingClick(building)
                    }
                focusLocation(item, isCampusBuilding, true)
            }
        } else if (item is IndoorLocation) {
            val bundle: Bundle = Bundle()
            bundle.putString("id", item.lID)
            val buildingInitial = item.name.split('-')[0]
            bundle.putString("floornum", item.floorNum)
            bundle.putString("floormap", item.floorMap)
            val building = viewModel.findBuildingByInitial(buildingInitial)
            bundle.putParcelable("building", building)
            findNavController().navigate(R.id.floor_fragment, bundle)
        }
    }

    private fun focusLocation(location: GooglePlace, isCampusBuilding : Boolean, isRequired : Boolean){
        GlobalScope.launch {
            if(isRequired) {
                viewModel.navigationRepository.fetchPlace(location)
            }
        }.invokeOnCompletion {
            requireActivity().runOnUiThread{
                moveTo(location.coordinate, 17.0f)
                if (!isCampusBuilding)
                    populatePlaceInfoCard(location)
            }
        }
    }

    private fun moveTo(coordinates: LatLng, zoomLevel: Float) {
        map!!.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates, zoomLevel))
    }

    private fun toggleSaveButton() {
        val saveButton : Button = requireActivity().findViewById(R.id.place_card_favorites_button)
        if (saveButton.text == "Save") {
            saveButton.text = "Saved"
            val redHeart : Drawable? = activity?.let { ContextCompat.getDrawable(it, R.drawable.ic_favorites_heart_red) }
            saveButton.setCompoundDrawablesWithIntrinsicBounds(redHeart, null, null, null)
        } else {
            saveButton.text = "Save"
            val greyHeart : Drawable? = activity?.let { ContextCompat.getDrawable(it, R.drawable.ic_favorites_heart_grey) }
            saveButton.setCompoundDrawablesWithIntrinsicBounds(greyHeart, null, null, null)
        }
    }

    private fun populatePlaceInfoCard(location: GooglePlace){
        val saveButton : Button = requireActivity().findViewById(R.id.place_card_favorites_button)
        val placeName: TextView = requireActivity().findViewById(R.id.place_card_name)
        val placeCategory: TextView = requireActivity().findViewById(R.id.place_card_category)
        val closeButton : ImageView = requireActivity().findViewById(R.id.place_card_close_button)
        toggleInstructionsView(false)

        placeName.text = location.name
        placeCategory.text = location.category

        val favoritesDb : FavoritesDatabase = viewModel.getFavoritesDb()
        val foundPlace : FavoritePlace? = favoritesDb.favoritePlacesDao().findPlaceById(location.placeID)
        if (foundPlace != null) {
            saveButton.text = "Saved"
            val redHeart : Drawable? = activity?.let { ContextCompat.getDrawable(it, R.drawable.ic_favorites_heart_red)}
            saveButton.setCompoundDrawablesWithIntrinsicBounds(redHeart, null, null, null)
        } else {
            saveButton.text = "Save"
            val greyHeart : Drawable? = activity?.let { ContextCompat.getDrawable(it, R.drawable.ic_favorites_heart_grey)}
            saveButton.setCompoundDrawablesWithIntrinsicBounds(greyHeart, null, null, null)
        }

        placeName.setOnClickListener {
            moveTo(location.coordinate, 17.0f)
        }

        closeButton.setOnClickListener{
            togglePlaceCard(false)
        }

        val directionsButton : Button = requireActivity().findViewById(R.id.place_card_directions_button)
        directionsButton.setOnClickListener {
            val bundle = Bundle()
            bundle.putParcelable("destPlace", location.place)
            findNavController().navigate(R.id.search_fragment, bundle)
        }

        saveButton.setOnClickListener {
            val foundFavoritePlace : FavoritePlace? = favoritesDb.favoritePlacesDao().findPlaceById(location.placeID)
            if (foundFavoritePlace != null) {
                favoritesDb.favoritePlacesDao().removePlace(foundFavoritePlace)
            } else {
                favoritesDb.favoritePlacesDao().savePlace(favoritesDb.favoritePlacesDao().createFavoritePlace(location))
            }

            toggleSaveButton()
        }

        togglePlaceCard(true)
    }

    private fun togglePlaceCard(isVisible : Boolean){
        val placeCard : CardView = requireActivity().findViewById(R.id.place_card)
        if(isVisible){
            placeCard.visibility = View.VISIBLE
            map?.setPadding(0, MAP_PADDING_TOP, MAP_PADDING_RIGHT, placeCard.height+75)
        }
        else{
            placeCard.visibility = View.INVISIBLE
            map?.setPadding(0, MAP_PADDING_TOP, MAP_PADDING_RIGHT, 0)
        }
    }

    /**
     * Notify observers whenever the camera of the Google map is idle
     */
    override fun onCameraIdle() {
        notifyObservers()
    }

    override fun attach(observer: ModifiedObserver?) {
        if (observer != null) {
            observerList.add(observer)
        }
    }

    override fun detach(observer: ModifiedObserver?) {
        if (observer != null) {
            observerList.remove(observer)
        }
    }

    override fun notifyObservers() {
        for (observer in observerList) {
            observer?.update(map!!.cameraPosition.zoom)
        }
    }

    override fun onFavoriteClick(item: FavoritePlace?) {
        if (item != null) {
            val googlePlace = GooglePlace(
                item.placeId,
                item.name,
                item.address ?: "",
                LatLng(item.latitude, item.longitude)
            )
            findNavController().popBackStack(R.id.map_fragment, false)
            GlobalScope.launch {
                viewModel.navigationRepository.fetchPlace(googlePlace)
            }.invokeOnCompletion {
                requireActivity().runOnUiThread {
                    moveTo(LatLng(item.latitude, item.longitude), 17.0f)
                    populatePlaceInfoCard(googlePlace)
                }
            }
        }
    }

    override fun onExplorePlaceClick(item: ExplorePlace?) {
        findNavController().popBackStack(R.id.map_fragment, false)
        val exploreLocation = GooglePlace(
            item?.placePlaceId!!,
            item.placeName!!,
            item.placeAddress!!,
            item.placeCoordinate
        )
        Handler().postDelayed({
            focusLocation(exploreLocation, false, true)
        }, 500)
    }

    private fun checkSettingOptions() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val set = HashSet<String>()
        val settingInflatedView: View = layoutInflater.inflate(R.layout.settings_fragment, null)

        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_stairs), "stairs")
        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_escalators), "escalators")
        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_elevators), "elevators")
        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_restrooms), "restrooms")
        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_printers), "printers")
        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_fountains), "fountains")
        populateSettingOffArray(settingInflatedView.findViewById(R.id.switch_settings_studyRooms), "study")

        set.addAll(preferenceOff)
        editor.putStringSet("settingOffArray", set)
        editor.apply()
    }

    private fun populateSettingOffArray(switchButton: Switch, buttonText: String) {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val default: Boolean = sharedPref.getBoolean(buttonText, true)
        switchButton.isChecked = default

        if(!switchButton.isChecked) {
            preferenceOff.add(buttonText)
        } else {
            preferenceOff.remove(buttonText)
        }
    }
}
