package cachet.plugins.health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.NonNull
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.Registrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil

const val CHANNEL_NAME = "flutter_health"

class HealthPlugin(private var channel: MethodChannel? = null) : MethodCallHandler, ActivityResultListener, Result,
    ActivityAware, FlutterPlugin {
    private var mResult: Result? = null
    private var handler: Handler? = null
    private var activity: Activity? = null
    private var context: Context? = null
    private var threadPoolExecutor: ExecutorService? = null

    private var useHealthConnectIfAvailable: Boolean = false
    private var healthConnectRequestPermissionsLauncher: ActivityResultLauncher<Set<String>>? = null
    private var healthConnectAvailable = false
    private var healthConnectStatus = HealthConnectClient.SDK_UNAVAILABLE
    private lateinit var healthConnectClient: HealthConnectClient

    private lateinit var scope: CoroutineScope

    private var STEPS = "STEPS"
    private var AGGREGATE_STEP_COUNT = "AGGREGATE_STEP_COUNT"
    private var ACTIVE_ENERGY_BURNED = "ACTIVE_ENERGY_BURNED"
    private var WORKOUT = "WORKOUT"



    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        channel?.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        threadPoolExecutor = Executors.newFixedThreadPool(4)
        checkAvailability()
        if (healthConnectAvailable) {
            healthConnectClient = HealthConnectClient.getOrCreate(flutterPluginBinding.applicationContext)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = null
        activity = null
        threadPoolExecutor!!.shutdown()
        threadPoolExecutor = null
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), CHANNEL_NAME)
            val plugin = HealthPlugin(channel)
            registrar.addActivityResultListener(plugin)
            channel.setMethodCallHandler(plugin)
        }
    }

    override fun success(p0: Any?) {
        handler?.post { mResult?.success(p0) }
    }

    override fun notImplemented() {
        handler?.post { mResult?.notImplemented() }
    }

    override fun error(
        errorCode: String,
        errorMessage: String?,
        errorDetails: Any?,
    ) {
        handler?.post { mResult?.error(errorCode, errorMessage, errorDetails) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return false
    }


    private fun onHealthConnectPermissionCallback(permissionGranted: Set<String>) {
        if (permissionGranted.isEmpty()) {
            mResult?.success(false);
            Log.i("FLUTTER_HEALTH", "Access Denied (to Health Connect)!")

        } else {
            mResult?.success(true);
            Log.i("FLUTTER_HEALTH", "Access Granted (to Health Connect)!")
        }

    }

    private fun getData(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            val dataType = call.argument<String>("dataTypeKey")!!
            val startTime = Instant.ofEpochMilli(call.argument<Long>("startTime")!!)
            val endTime = Instant.ofEpochMilli(call.argument<Long>("endTime")!!)
            val healthConnectData = mutableListOf<Map<String, Any?>>()
            scope.launch {
                toHealthConnectType[dataType]?.let { classType ->
                    val records = mutableListOf<Record>()

                    // Set up the initial request to read health records with specified parameters
                    var request = ReadRecordsRequest(
                        recordType = classType,
                        // Define the maximum amount of data that HealthConnect can return in a single request
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    )

                    var response = healthConnectClient.readRecords(request)
                    var pageToken = response.pageToken

                    // Add the records from the initial response to the records list
                    records.addAll(response.records)

                    // Continue making requests and fetching records while there is a page token
                    while (!pageToken.isNullOrEmpty()) {
                        request = ReadRecordsRequest(
                            recordType = classType,
                            timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                            pageToken = pageToken
                        )
                        response = healthConnectClient.readRecords(request)

                        pageToken = response.pageToken
                        records.addAll(response.records)
                    }

                    // Workout needs distance and total calories burned too
                    if (dataType == WORKOUT) {
                        for (rec in records) {
                            val record = rec as ExerciseSessionRecord
                            val distanceRequest = healthConnectClient.readRecords(
                                ReadRecordsRequest(
                                    recordType = DistanceRecord::class,
                                    timeRangeFilter = TimeRangeFilter.between(
                                        record.startTime,
                                        record.endTime,
                                    ),
                                ),
                            )
                            var totalDistance = 0.0
                            for (distanceRec in distanceRequest.records) {
                                totalDistance += distanceRec.distance.inMeters
                            }

                            val energyBurnedRequest = healthConnectClient.readRecords(
                                ReadRecordsRequest(
                                    recordType = TotalCaloriesBurnedRecord::class,
                                    timeRangeFilter = TimeRangeFilter.between(
                                        record.startTime,
                                        record.endTime,
                                    ),
                                ),
                            )
                            var totalEnergyBurned = 0.0
                            for (energyBurnedRec in energyBurnedRequest.records) {
                                totalEnergyBurned += energyBurnedRec.energy.inKilocalories
                            }

                            // val metadata = (rec as Record).metadata
                            // Add final datapoint
                            healthConnectData.add(
                                // mapOf(
                                mapOf<String, Any?>(
                                    "workoutActivityType" to (workoutTypeMapHealthConnect.filterValues { it == record.exerciseType }.keys.firstOrNull()
                                        ?: "OTHER"),
                                    "totalDistance" to if (totalDistance == 0.0) null else totalDistance,
                                    "totalDistanceUnit" to "METER",
                                    "totalEnergyBurned" to if (totalEnergyBurned == 0.0) null else totalEnergyBurned,
                                    "totalEnergyBurnedUnit" to "KILOCALORIE",
                                    "unit" to "MINUTES",
                                    "date_from" to rec.startTime.toEpochMilli(),
                                    "date_to" to rec.endTime.toEpochMilli(),
                                    "source_id" to "",
                                    "source_name" to record.metadata.dataOrigin.packageName,
                                ),
                            )
                        }
                        // Filter sleep stages for requested stage
                    } else {
                        for (rec in records) {
                            healthConnectData.addAll(convertRecord(rec))
                        }
                    }
                }
                Handler(context!!.mainLooper).run { result.success(healthConnectData) }
            }
            return
        }

        if (context == null) {
            result.success(null)
            return
        }

    }


    private fun hasPermissions(result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            scope.launch {
                result.success(
                    healthConnectClient.permissionController.getGrantedPermissions().containsAll(
                        listOf(
                            HealthPermission.getReadPermission(StepsRecord::class),
                            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
                        )
                    ),
                )
            }
            return
        }
    }

    /**
     * Requests authorization for the HealthDataTypes
     * with the the READ or READ_WRITE permission type.
     */
    private fun requestAuthorization(result: Result) {
        if (context == null) {
            result.success(false)
            return
        }
        mResult = result

        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            if (healthConnectRequestPermissionsLauncher == null) {
                result.success(false)
                Log.i("FLUTTER_HEALTH", "Permission launcher not found")
                return
            }
            healthConnectRequestPermissionsLauncher!!.launch(
                listOf(
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
                ).toSet()
            )
            return
        }
    }

    private fun getTotalStepsInInterval(call: MethodCall, result: Result) {
        val start = call.argument<Long>("startTime")!!
        val end = call.argument<Long>("endTime")!!

        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            getStepsHealthConnect(start, end, result)
            return
        }
    }



    private fun getTotalStepAndCaloriesInInterval(call: MethodCall, result: Result) {
        val start = call.argument<Long>("startTime")!!
        val end = call.argument<Long>("endTime")!!

        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            getStepsAndCaloriesHealthConnect(start, end, result)
            return
        }
    }

    private fun getStepsAndCaloriesHealthConnect(start: Long, end: Long, result: Result) = scope.launch {
        try {
            val startInstant = Instant.ofEpochMilli(start)
            val endInstant = Instant.ofEpochMilli(end)
            val response = healthConnectClient.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    timeRangeSlicer = Duration.ofDays(1L),
                ),

                )

            val heathData = mutableListOf<Map<String, Any?>>();

            for (bucket in response) {
                val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L
                val dateFrom = bucket.startTime.toEpochMilli()
                val endTime = bucket.endTime.toEpochMilli()
                val caloRows = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = TotalCaloriesBurnedRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(bucket.startTime, bucket.endTime),
                    ),
                )
                var calories = 0.0
                if (caloRows.records.isNotEmpty()) {
                    calories = caloRows.records.sumOf { it.energy.inKilocalories }
                }
                heathData.add(
                    mapOf(
                        "steps" to steps,
                        "calories" to ceil(calories),
                        "date_from" to dateFrom,
                        "date_to" to endTime,
                    )
                )
            }
            result.success(heathData)
        } catch (e: Exception) {
            Log.i("FLUTTER_HEALTH::ERROR", "unable to return steps")
            Log.i("EXCEPTION", e.message.toString())
            result.success(null)
        }
    }

    private fun getStepsHealthConnect(start: Long, end: Long, result: Result) = scope.launch {
        try {
            val startInstant = Instant.ofEpochMilli(start)
            val endInstant = Instant.ofEpochMilli(end)
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                ),
            )
            // The result may be null if no data is available in the time range.
            val stepsInInterval = response[StepsRecord.COUNT_TOTAL] ?: 0L
            Log.i("FLUTTER_HEALTH::SUCCESS", "returning $stepsInInterval steps")
            result.success(stepsInInterval)
        } catch (e: Exception) {
            Log.i("FLUTTER_HEALTH::ERROR", "unable to return steps")
            result.success(null)
        }
    }

    private fun checkAvailability() {
        healthConnectStatus = HealthConnectClient.getSdkStatus(context!!)
        healthConnectAvailable = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE
    }

    private fun useHealthConnectIfAvailable(result: Result) {
        useHealthConnectIfAvailable = true
        result.success(null)
    }
    private fun convertRecord(record: Any): List<Map<String, Any>> {
        val metadata = (record as Record).metadata
        when (record) {
            is StepsRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.count,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is ActiveCaloriesBurnedRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.energy.inKilocalories,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            else -> throw IllegalArgumentException("Health data type not supported") // TODO: Exception or error?
        }
    }

    private fun getHCData(call: MethodCall, result: Result) {

    }

    /**
     *  Handle calls from the MethodChannel
     */
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "useHealthConnectIfAvailable" -> useHealthConnectIfAvailable(result)
            "hasPermissions" -> hasPermissions(result)
            "requestAuthorization" -> requestAuthorization(result)
            "getData" -> getData(call, result)
            "getTotalStepsInInterval" -> getTotalStepsInInterval(call, result)
            "getTotalStepAndCaloriesInInterval" -> getTotalStepAndCaloriesInInterval(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if (channel == null) {
            return
        }
        binding.addActivityResultListener(this)
        activity = binding.activity


        if (healthConnectAvailable) {
            val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()

            healthConnectRequestPermissionsLauncher =
                (activity as ComponentActivity).registerForActivityResult(requestPermissionActivityContract) { granted ->
                    onHealthConnectPermissionCallback(granted);
                }
        }

    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        if (channel == null) {
            return
        }
        activity = null
        healthConnectRequestPermissionsLauncher = null;
    }


    // TODO: Update with new workout types when Health Connect becomes the standard.
    private val workoutTypeMapHealthConnect = mapOf(
        // "AEROBICS" to ExerciseSessionRecord.EXERCISE_TYPE_AEROBICS,
        "AMERICAN_FOOTBALL" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
        // "ARCHERY" to ExerciseSessionRecord.EXERCISE_TYPE_ARCHERY,
        "AUSTRALIAN_FOOTBALL" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
        "BADMINTON" to ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON,
        "BASEBALL" to ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL,
        "BASKETBALL" to ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
        // "BIATHLON" to ExerciseSessionRecord.EXERCISE_TYPE_BIATHLON,
        "BIKING" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        // "BIKING_HAND" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_HAND,
        //"BIKING_MOUNTAIN" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_MOUNTAIN,
        // "BIKING_ROAD" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_ROAD,
        // "BIKING_SPINNING" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_SPINNING,
        // "BIKING_STATIONARY" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        // "BIKING_UTILITY" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_UTILITY,
        "BOXING" to ExerciseSessionRecord.EXERCISE_TYPE_BOXING,
        "CALISTHENICS" to ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        // "CIRCUIT_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_CIRCUIT_TRAINING,
        "CRICKET" to ExerciseSessionRecord.EXERCISE_TYPE_CRICKET,
        // "CROSS_COUNTRY_SKIING" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING_CROSS_COUNTRY,
        // "CROSS_FIT" to ExerciseSessionRecord.EXERCISE_TYPE_CROSSFIT,
        // "CURLING" to ExerciseSessionRecord.EXERCISE_TYPE_CURLING,
        "DANCING" to ExerciseSessionRecord.EXERCISE_TYPE_DANCING,
        // "DIVING" to ExerciseSessionRecord.EXERCISE_TYPE_DIVING,
        // "DOWNHILL_SKIING" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING_DOWNHILL,
        // "ELEVATOR" to ExerciseSessionRecord.EXERCISE_TYPE_ELEVATOR,
        "ELLIPTICAL" to ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        // "ERGOMETER" to ExerciseSessionRecord.EXERCISE_TYPE_ERGOMETER,
        // "ESCALATOR" to ExerciseSessionRecord.EXERCISE_TYPE_ESCALATOR,
        "FENCING" to ExerciseSessionRecord.EXERCISE_TYPE_FENCING,
        "FRISBEE_DISC" to ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC,
        // "GARDENING" to ExerciseSessionRecord.EXERCISE_TYPE_GARDENING,
        "GOLF" to ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
        "GUIDED_BREATHING" to ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
        "GYMNASTICS" to ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS,
        "HANDBALL" to ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL,
        "HIGH_INTENSITY_INTERVAL_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        "HIKING" to ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        // "HOCKEY" to ExerciseSessionRecord.EXERCISE_TYPE_HOCKEY,
        // "HORSEBACK_RIDING" to ExerciseSessionRecord.EXERCISE_TYPE_HORSEBACK_RIDING,
        // "HOUSEWORK" to ExerciseSessionRecord.EXERCISE_TYPE_HOUSEWORK,
        // "IN_VEHICLE" to ExerciseSessionRecord.EXERCISE_TYPE_IN_VEHICLE,
        "ICE_SKATING" to ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING,
        // "INTERVAL_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_INTERVAL_TRAINING,
        // "JUMP_ROPE" to ExerciseSessionRecord.EXERCISE_TYPE_JUMP_ROPE,
        // "KAYAKING" to ExerciseSessionRecord.EXERCISE_TYPE_KAYAKING,
        // "KETTLEBELL_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_KETTLEBELL_TRAINING,
        // "KICK_SCOOTER" to ExerciseSessionRecord.EXERCISE_TYPE_KICK_SCOOTER,
        // "KICKBOXING" to ExerciseSessionRecord.EXERCISE_TYPE_KICKBOXING,
        // "KITE_SURFING" to ExerciseSessionRecord.EXERCISE_TYPE_KITESURFING,
        "MARTIAL_ARTS" to ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
        // "MEDITATION" to ExerciseSessionRecord.EXERCISE_TYPE_MEDITATION,
        // "MIXED_MARTIAL_ARTS" to ExerciseSessionRecord.EXERCISE_TYPE_MIXED_MARTIAL_ARTS,
        // "P90X" to ExerciseSessionRecord.EXERCISE_TYPE_P90X,
        "PARAGLIDING" to ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING,
        "PILATES" to ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        // "POLO" to ExerciseSessionRecord.EXERCISE_TYPE_POLO,
        "RACQUETBALL" to ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL,
        "ROCK_CLIMBING" to ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
        "ROWING" to ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        "ROWING_MACHINE" to ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        "RUGBY" to ExerciseSessionRecord.EXERCISE_TYPE_RUGBY,
        // "RUNNING_JOGGING" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_JOGGING,
        // "RUNNING_SAND" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_SAND,
        "RUNNING_TREADMILL" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        "RUNNING" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        "SAILING" to ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
        "SCUBA_DIVING" to ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING,
        // "SKATING_CROSS" to ExerciseSessionRecord.EXERCISE_TYPE_SKATING_CROSS,
        // "SKATING_INDOOR" to ExerciseSessionRecord.EXERCISE_TYPE_SKATING_INDOOR,
        // "SKATING_INLINE" to ExerciseSessionRecord.EXERCISE_TYPE_SKATING_INLINE,
        "SKATING" to ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
        "SKIING" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
        // "SKIING_BACK_COUNTRY" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING_BACK_COUNTRY,
        // "SKIING_KITE" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING_KITE,
        // "SKIING_ROLLER" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING_ROLLER,
        // "SLEDDING" to ExerciseSessionRecord.EXERCISE_TYPE_SLEDDING,
        "SNOWBOARDING" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
        // "SNOWMOBILE" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWMOBILE,
        "SNOWSHOEING" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
        // "SOCCER" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_SOCCER,
        "SOFTBALL" to ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL,
        "SQUASH" to ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
        "STAIR_CLIMBING_MACHINE" to ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        "STAIR_CLIMBING" to ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        // "STANDUP_PADDLEBOARDING" to ExerciseSessionRecord.EXERCISE_TYPE_STANDUP_PADDLEBOARDING,
        // "STILL" to ExerciseSessionRecord.EXERCISE_TYPE_STILL,
        "STRENGTH_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        "SURFING" to ExerciseSessionRecord.EXERCISE_TYPE_SURFING,
        "SWIMMING_OPEN_WATER" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        "SWIMMING_POOL" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        // "SWIMMING" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING,
        "TABLE_TENNIS" to ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS,
        // "TEAM_SPORTS" to ExerciseSessionRecord.EXERCISE_TYPE_TEAM_SPORTS,
        "TENNIS" to ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
        // "TILTING" to ExerciseSessionRecord.EXERCISE_TYPE_TILTING,
        // "VOLLEYBALL_BEACH" to ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL_BEACH,
        // "VOLLEYBALL_INDOOR" to ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL_INDOOR,
        "VOLLEYBALL" to ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL,
        // "WAKEBOARDING" to ExerciseSessionRecord.EXERCISE_TYPE_WAKEBOARDING,
        // "WALKING_FITNESS" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING_FITNESS,
        // "WALKING_PACED" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING_PACED,
        // "WALKING_NORDIC" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING_NORDIC,
        // "WALKING_STROLLER" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING_STROLLER,
        // "WALKING_TREADMILL" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING_TREADMILL,
        "WALKING" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        "WATER_POLO" to ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO,
        "WEIGHTLIFTING" to ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        "WHEELCHAIR" to ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR,
        // "WINDSURFING" to ExerciseSessionRecord.EXERCISE_TYPE_WINDSURFING,
        "YOGA" to ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        // "ZUMBA" to ExerciseSessionRecord.EXERCISE_TYPE_ZUMBA,
        // "OTHER" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER,
    )


    private val toHealthConnectType = hashMapOf(
        STEPS to StepsRecord::class,
        AGGREGATE_STEP_COUNT to StepsRecord::class,
        ACTIVE_ENERGY_BURNED to ActiveCaloriesBurnedRecord::class,
        WORKOUT to ExerciseSessionRecord::class,
    )
}
