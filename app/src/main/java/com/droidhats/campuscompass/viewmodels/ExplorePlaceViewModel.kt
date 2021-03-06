package com.droidhats.campuscompass.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.droidhats.campuscompass.models.ExplorePlace
import com.droidhats.campuscompass.repositories.ExplorePlaceRepository

/**
 * This class will provide places to explore to the fragment class
 * And will interact with the ExplorePlacesRepository to fetch the data
 * @param application
 */
class ExplorePlaceViewModel (application: Application) : AndroidViewModel(application){

    private var explorePlaceRepository: ExplorePlaceRepository = ExplorePlaceRepository.getInstance(getApplication())
    private  var placesList = MutableLiveData<ArrayList<ExplorePlace>>()

    fun fetchPlaces(campus:String, type: String){
        explorePlaceRepository.getPlaces(campus, type)
    }

    fun getPlaces() : MutableLiveData<ArrayList<ExplorePlace>> {
        placesList = explorePlaceRepository.placesList
        return  placesList
    }
}