package com.droidhats.campuscompass.repositories

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.droidhats.mapprocessor.ProcessMap
import com.droidhats.campuscompass.models.Building
import com.droidhats.campuscompass.models.IndoorLocation
import org.json.JSONObject
import java.io.InputStream

class IndoorLocationRepository private constructor(private val indoorLocationDao: IndoorLocationDao) {

    companion object {
        // Singleton instantiation
        private var instance: IndoorLocationRepository? = null

        fun getInstance(indoorLocationDao : IndoorLocationDao ) =
            instance
                ?: synchronized(this) {
                    instance
                        ?: IndoorLocationRepository(indoorLocationDao).also { instance = it }
                }

        private var buildingNumberMap: MutableMap<String, MutableMap<Int, Int>> = mutableMapOf()
    }

    fun getIndoorLocations() : List<IndoorLocation> = indoorLocationDao.getAll()
    fun getClassrooms() : LiveData<List<IndoorLocation>> = indoorLocationDao.getAllClassrooms()
    fun getMatchedClassrooms(query : SimpleSQLiteQuery) : LiveData<List<IndoorLocation>>
            = indoorLocationDao.getMatchedClassrooms(query)
    fun insertIndoorLocation(loc: IndoorLocation) = indoorLocationDao.insertIndoorLocation(loc)

    fun initializeIndoorLocations(context: Context, map: MapRepository) {
        val inputStream: InputStream = context.assets.open("config.json")
        val json: String = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(json)
        val config: String = jsonObject.getString("mode")
        if (config == "debug") {
            indoorLocationDao.deleteAllIndoor()
        }
        if (config == "debug" || indoorLocationDao.getOne().value == null) {
            map.forEachBuilding { building ->
                insertClasses(context, building)
            }
        }
    }

    fun insertClasses(context: Context, building: Building) {

        for (floorMap in building.getIndoorInfo().second) {
            val inputStream: InputStream = context.assets.open(floorMap)
            val file: String = inputStream.bufferedReader().use { it.readText() }
            val mapProcessor: ProcessMap = ProcessMap()
            mapProcessor.readSVGFromString(file)
            val classes = mapProcessor.getClasses()

            // todo: Consider the case where floor number is more than 1 digit
            val floorValue: String = floorMap.split(building.getIndoorInfo().first)[1].split(".svg")[0]
            val floorNumber: Int = Character.getNumericValue(floorValue[0])
            for ((x, classRoom) in classes.withIndex()) {
                if (classRoom.getID()[5] != floorValue[0]) {
                    continue
                }
                val newClass = IndoorLocation(
                    classRoom.getID(),
                    convertIDToName(classRoom.getID(), building.getIndoorInfo().first, floorNumber),
                    floorNumber,
                    "classroom",
                    building.coordinate.latitude,
                    building.coordinate.longitude
                )
                indoorLocationDao.insertIndoorLocation(newClass)
            }
        }

    }

    /**
     * Converts id, building name and floor number into the proper name
     * for the appropriate Class Room
     * @param id This is the id generated from the svg file
     * @param buildingName This is the name for which the room belongs
     * @param floorNumber This is the number of the floor within the building
     * @return returns the string of the generated room name
     */
    fun convertIDToName(id: String, buildingName: String, floorNumber: Int): String {
        return buildingName + "-" + floorNumber.toString() + id.substring(6, id.length)
    }
}
