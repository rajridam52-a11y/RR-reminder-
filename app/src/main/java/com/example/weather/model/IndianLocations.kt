package com.example.weather.model

data class CityLocation(
    val name: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val isStateCapital: Boolean = false
)

object IndianLocations {

    val statesAndUTs = listOf(
        // 28 States
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
        "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand",
        "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
        "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
        "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura",
        "Uttar Pradesh", "Uttarakhand", "West Bengal",
        // 8 Union Territories
        "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
        "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
    )

    val citiesList = listOf(
        CityLocation("Amaravati", "Andhra Pradesh", 16.5131, 80.5165, true),
        CityLocation("Visakhapatnam", "Andhra Pradesh", 17.6868, 83.2185),
        CityLocation("Itanagar", "Arunachal Pradesh", 27.0844, 93.6053, true),
        CityLocation("Dispur", "Assam", 26.1433, 91.7898, true),
        CityLocation("Guwahati", "Assam", 26.1445, 91.7362),
        CityLocation("Patna", "Bihar", 25.5941, 85.1376, true),
        CityLocation("Raipur", "Chhattisgarh", 21.2514, 81.6296, true),
        CityLocation("Bilaspur", "Chhattisgarh", 22.0797, 82.1409),
        CityLocation("Panaji", "Goa", 15.4909, 73.8278, true),
        CityLocation("Gandhinagar", "Gujarat", 23.2156, 72.6369, true),
        CityLocation("Ahmedabad", "Gujarat", 23.0225, 72.5714),
        CityLocation("Chandigarh", "Haryana", 30.7333, 76.7794, true),
        CityLocation("Gurugram", "Haryana", 28.4595, 77.0266),
        CityLocation("Shimla", "Himachal Pradesh", 31.1048, 77.1734, true),
        CityLocation("Ranchi", "Jharkhand", 23.3441, 85.3096, true),
        CityLocation("Bengaluru", "Karnataka", 12.9716, 77.5946, true),
        CityLocation("Thiruvananthapuram", "Kerala", 8.5241, 76.9366, true),
        CityLocation("Kochi", "Kerala", 9.9312, 76.2673),
        CityLocation("Bhopal", "Madhya Pradesh", 23.2599, 77.4126, true),
        CityLocation("Indore", "Madhya Pradesh", 22.7196, 75.8577),
        CityLocation("Mumbai", "Maharashtra", 19.0760, 72.8777, true),
        CityLocation("Pune", "Maharashtra", 18.5204, 73.8567),
        CityLocation("Nagpur", "Maharashtra", 21.1458, 79.0882),
        CityLocation("Imphal", "Manipur", 24.8170, 93.9368, true),
        CityLocation("Shillong", "Meghalaya", 25.5788, 91.8933, true),
        CityLocation("Aizawl", "Mizoram", 23.7271, 92.7176, true),
        CityLocation("Kohima", "Nagaland", 25.6751, 94.1086, true),
        CityLocation("Bhubaneswar", "Odisha", 20.2961, 85.8245, true),
        CityLocation("Chandigarh", "Punjab", 30.7333, 76.7794, true),
        CityLocation("Amritsar", "Punjab", 31.6340, 74.8723),
        CityLocation("Jaipur", "Rajasthan", 26.9124, 75.7873, true),
        CityLocation("Gangtok", "Sikkim", 27.3389, 88.6065, true),
        CityLocation("Chennai", "Tamil Nadu", 13.0827, 80.2707, true),
        CityLocation("Coimbatore", "Tamil Nadu", 11.0168, 76.9558),
        CityLocation("Hyderabad", "Telangana", 17.3850, 78.4867, true),
        CityLocation("Agartala", "Tripura", 23.8315, 91.2868, true),
        CityLocation("Lucknow", "Uttar Pradesh", 26.8467, 80.9462, true),
        CityLocation("Noida", "Uttar Pradesh", 28.5355, 77.3910),
        CityLocation("Varanasi", "Uttar Pradesh", 25.3176, 82.9739),
        CityLocation("Dehradun", "Uttarakhand", 30.3165, 78.0322, true),
        CityLocation("Kolkata", "West Bengal", 22.5726, 88.3639, true),
        // UT Capitals/Hubs
        CityLocation("Port Blair", "Andaman and Nicobar Islands", 11.6234, 92.7265, true),
        CityLocation("Daman", "Dadra and Nagar Haveli and Daman and Diu", 20.3974, 72.8328, true),
        CityLocation("New Delhi", "Delhi", 28.6139, 77.2090, true),
        CityLocation("Srinagar", "Jammu and Kashmir", 34.0837, 74.7973, true),
        CityLocation("Jammu", "Jammu and Kashmir", 32.7266, 74.8570),
        CityLocation("Leh", "Ladakh", 34.1526, 77.5771, true),
        CityLocation("Kavaratti", "Lakshadweep", 10.5667, 72.6417, true),
        CityLocation("Puducherry", "Puducherry", 11.9416, 79.8083, true)
    )

    fun getCitiesForState(stateName: String): List<CityLocation> {
        return citiesList.filter { it.state.equals(stateName, ignoreCase = true) }
    }

    fun findCityByName(cityName: String): CityLocation? {
        return citiesList.firstOrNull { it.name.equals(cityName, ignoreCase = true) }
    }
}
