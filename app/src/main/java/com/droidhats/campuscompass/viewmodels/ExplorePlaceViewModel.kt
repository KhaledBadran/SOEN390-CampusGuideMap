package com.droidhats.campuscompass.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.droidhats.campuscompass.repositories.ExplorePlaceRepository
import com.droidhats.campuscompass.roomdb.ExplorePlaceEntity

/**
 * This class will provide places to explore to the fragment class
 * And will interact with the ExplorePlacesRepository to fetch the data
 * @param application
 */
class ExplorePlaceViewModel (application: Application) : AndroidViewModel(application){

    private  var explorePlaceRepository: ExplorePlaceRepository = ExplorePlaceRepository.getInstance(getApplication())
    private  var allPlaces: LiveData<List<ExplorePlaceEntity>>


    init {
        allPlaces  =  explorePlaceRepository.getAllPlaces()
    }

    /**
     * @return allPlaces
     */
    fun getAllPlaces(): LiveData<List<ExplorePlaceEntity>> {
        return allPlaces
    }
}