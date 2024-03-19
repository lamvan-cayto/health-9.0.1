part of health;

/// List of all available data types.
enum HealthDataType {
  ACTIVE_ENERGY_BURNED,
  STEPS,
}

/// Access types for Health Data.
enum HealthDataAccess {
  READ,
  WRITE,
  READ_WRITE,
}

/// List of data types available on iOS
const List<HealthDataType> _dataTypeKeysIOS = [
  HealthDataType.ACTIVE_ENERGY_BURNED,
  HealthDataType.STEPS,
];

/// List of data types available on Android
const List<HealthDataType> _dataTypeKeysAndroid = [
  HealthDataType.ACTIVE_ENERGY_BURNED,
];

/// Maps a [HealthDataType] to a [HealthDataUnit].
const Map<HealthDataType, HealthDataUnit> _dataTypeToUnit = {
  HealthDataType.ACTIVE_ENERGY_BURNED: HealthDataUnit.KILOCALORIE,
  HealthDataType.STEPS: HealthDataUnit.COUNT,
};

const PlatformTypeJsonValue = {
  PlatformType.IOS: 'ios',
  PlatformType.ANDROID: 'android',
};

/// List of all [HealthDataUnit]s.
enum HealthDataUnit {
  // Mass units
  GRAM,
  KILOGRAM,
  OUNCE,
  POUND,
  STONE,
  // MOLE_UNIT_WITH_MOLAR_MASS, // requires molar mass input - not supported yet
  // MOLE_UNIT_WITH_PREFIX_MOLAR_MASS, // requires molar mass & prefix input - not supported yet

  // Length units
  METER,
  INCH,
  FOOT,
  YARD,
  MILE,

  // Volume units
  LITER,
  MILLILITER,
  FLUID_OUNCE_US,
  FLUID_OUNCE_IMPERIAL,
  CUP_US,
  CUP_IMPERIAL,
  PINT_US,
  PINT_IMPERIAL,

  // Pressure units
  PASCAL,
  MILLIMETER_OF_MERCURY,
  INCHES_OF_MERCURY,
  CENTIMETER_OF_WATER,
  ATMOSPHERE,
  DECIBEL_A_WEIGHTED_SOUND_PRESSURE_LEVEL,

  // Time units
  SECOND,
  MILLISECOND,
  MINUTE,
  HOUR,
  DAY,

  // Energy units
  JOULE,
  KILOCALORIE,
  LARGE_CALORIE,
  SMALL_CALORIE,

  // Temperature units
  DEGREE_CELSIUS,
  DEGREE_FAHRENHEIT,
  KELVIN,

  // Hearing units
  DECIBEL_HEARING_LEVEL,

  // Frequency units
  HERTZ,

  // Electrical conductance units
  SIEMEN,

  // Potential units
  VOLT,

  // Pharmacology units
  INTERNATIONAL_UNIT,

  // Scalar units
  COUNT,
  PERCENT,

  // Other units
  BEATS_PER_MINUTE,
  RESPIRATIONS_PER_MINUTE,
  MILLIGRAM_PER_DECILITER,
  UNKNOWN_UNIT,
  NO_UNIT,
}

/// List of [HealthWorkoutActivityType]s.
/// Commented for which platform they are supported
enum HealthWorkoutActivityType {
  // Both
  ARCHERY,
  BADMINTON,
  BASEBALL,
  BASKETBALL,
  BIKING, // This also entails the iOS version where it is called CYCLING
  BOXING,
  CRICKET,
  CURLING,
  ELLIPTICAL,
  FENCING,
  AMERICAN_FOOTBALL,
  AUSTRALIAN_FOOTBALL,
  SOCCER,
  GOLF,
  GYMNASTICS,
  HANDBALL,
  HIGH_INTENSITY_INTERVAL_TRAINING,
  HIKING,
  HOCKEY,
  SKATING,
  JUMP_ROPE,
  KICKBOXING,
  MARTIAL_ARTS,
  PILATES,
  RACQUETBALL,
  ROWING,
  RUGBY,
  RUNNING,
  SAILING,
  CROSS_COUNTRY_SKIING,
  DOWNHILL_SKIING,
  SNOWBOARDING,
  SOFTBALL,
  SQUASH,
  STAIR_CLIMBING,
  SWIMMING,
  TABLE_TENNIS,
  TENNIS,
  VOLLEYBALL,
  WALKING,
  WATER_POLO,
  YOGA,

  // iOS only
  BOWLING,
  CROSS_TRAINING,
  TRACK_AND_FIELD,
  DISC_SPORTS,
  LACROSSE,
  PREPARATION_AND_RECOVERY,
  FLEXIBILITY,
  COOLDOWN,
  WHEELCHAIR_WALK_PACE,
  WHEELCHAIR_RUN_PACE,
  HAND_CYCLING,
  CORE_TRAINING,
  FUNCTIONAL_STRENGTH_TRAINING,
  TRADITIONAL_STRENGTH_TRAINING,
  MIXED_CARDIO,
  STAIRS,
  STEP_TRAINING,
  FITNESS_GAMING,
  BARRE,
  CARDIO_DANCE,
  SOCIAL_DANCE,
  MIND_AND_BODY,
  PICKLEBALL,
  CLIMBING,
  EQUESTRIAN_SPORTS,
  FISHING,
  HUNTING,
  PLAY,
  SNOW_SPORTS,
  PADDLE_SPORTS,
  SURFING_SPORTS,
  WATER_FITNESS,
  WATER_SPORTS,
  TAI_CHI,
  WRESTLING,

  // Android only
  AEROBICS,
  BIATHLON,
  BIKING_HAND,
  BIKING_MOUNTAIN,
  BIKING_ROAD,
  BIKING_SPINNING,
  BIKING_STATIONARY,
  BIKING_UTILITY,
  CALISTHENICS,
  CIRCUIT_TRAINING,
  CROSS_FIT,
  DANCING,
  DIVING,
  ELEVATOR,
  ERGOMETER,
  ESCALATOR,
  FRISBEE_DISC,
  GARDENING,
  GUIDED_BREATHING,
  HORSEBACK_RIDING,
  HOUSEWORK,
  INTERVAL_TRAINING,
  IN_VEHICLE,
  ICE_SKATING,
  KAYAKING,
  KETTLEBELL_TRAINING,
  KICK_SCOOTER,
  KITE_SURFING,
  MEDITATION,
  MIXED_MARTIAL_ARTS,
  P90X,
  PARAGLIDING,
  POLO,
  ROCK_CLIMBING, // on iOS this is the same as CLIMBING
  ROWING_MACHINE,
  RUNNING_JOGGING, // on iOS this is the same as RUNNING
  RUNNING_SAND, // on iOS this is the same as RUNNING
  RUNNING_TREADMILL, // on iOS this is the same as RUNNING
  SCUBA_DIVING,
  SKATING_CROSS, // on iOS this is the same as SKATING
  SKATING_INDOOR, // on iOS this is the same as SKATING
  SKATING_INLINE, // on iOS this is the same as SKATING
  SKIING,
  SKIING_BACK_COUNTRY,
  SKIING_KITE,
  SKIING_ROLLER,
  SLEDDING,
  SNOWMOBILE,
  SNOWSHOEING,
  STAIR_CLIMBING_MACHINE,
  STANDUP_PADDLEBOARDING,
  STILL,
  STRENGTH_TRAINING,
  SURFING,
  SWIMMING_OPEN_WATER,
  SWIMMING_POOL,
  TEAM_SPORTS,
  TILTING,
  VOLLEYBALL_BEACH,
  VOLLEYBALL_INDOOR,
  WAKEBOARDING,
  WALKING_FITNESS,
  WALKING_NORDIC,
  WALKING_STROLLER,
  WALKING_TREADMILL,
  WEIGHTLIFTING,
  WHEELCHAIR,
  WINDSURFING,
  ZUMBA,

  //
  OTHER,
}

enum MealType {
  BREAKFAST,
  LUNCH,
  DINNER,
  SNACK,
  UNKNOWN,
}

/// Classifications for ECG readings.
enum ElectrocardiogramClassification {
  NOT_SET,
  SINUS_RHYTHM,
  ATRIAL_FIBRILLATION,
  INCONCLUSIVE_LOW_HEART_RATE,
  INCONCLUSIVE_HIGH_HEART_RATE,
  INCONCLUSIVE_POOR_READING,
  INCONCLUSIVE_OTHER,
  UNRECOGNIZED,
}

/// Extension to assign numbers to [ElectrocardiogramClassification]s
extension ElectrocardiogramClassificationValue
    on ElectrocardiogramClassification {
  int get value {
    switch (this) {
      case ElectrocardiogramClassification.NOT_SET:
        return 0;
      case ElectrocardiogramClassification.SINUS_RHYTHM:
        return 1;
      case ElectrocardiogramClassification.ATRIAL_FIBRILLATION:
        return 2;
      case ElectrocardiogramClassification.INCONCLUSIVE_LOW_HEART_RATE:
        return 3;
      case ElectrocardiogramClassification.INCONCLUSIVE_HIGH_HEART_RATE:
        return 4;
      case ElectrocardiogramClassification.INCONCLUSIVE_POOR_READING:
        return 5;
      case ElectrocardiogramClassification.INCONCLUSIVE_OTHER:
        return 6;
      case ElectrocardiogramClassification.UNRECOGNIZED:
        return 100;
    }
  }
}
