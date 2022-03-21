package org.team2471.frc2022

import com.revrobotics.ColorSensorV3
import edu.wpi.first.math.filter.LinearFilter
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DriverStation
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.I2C
import edu.wpi.first.wpilibj.Timer
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.math.Vector2
import org.team2471.frc.lib.math.round
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.asFeet
import org.team2471.frc.lib.units.degrees
import kotlin.math.absoluteValue


object Shooter : Subsystem("Shooter") {
    val tuningMode = true
    val shootingMotor = MotorController(FalconID(Falcons.SHOOTER), FalconID(Falcons.SHOOTER_TWO)) //private
    val pitchMotor = MotorController(TalonID(Talons.PITCH))
    private val table = NetworkTableInstance.getDefault().getTable(name)
    val pitchEncoder = DutyCycleEncoder(DigitalSensors.SHOOTER_PITCH)
    val rpmErrorFilter = LinearFilter.movingAverage(4)

    private val i2cPort: I2C.Port = I2C.Port.kMXP
    private val colorSensor = ColorSensorV3(i2cPort)
    val colorEntry = table.getEntry("Color")
    val isKnownShot : knownShotType
        get() {
            return knownShotType.valueOf(SmartDashboard.getString("KnownShot/selected", "notset").uppercase())
        }
    const val aimMaxError = 3.0
    const val rpmMaxError = 500.0
    const val pitchMaxError = 5.0

    enum class knownShotType {
        NOTSET, FENDER, WALL, SAFE
    }

    val rpmEntry = table.getEntry("RPM")
    val rpmSetpointEntry = table.getEntry("RPM Setpoint")
    val aimMaxErrorEntry = table.getEntry("aimMaxError")
    val pitchErrorEntry = table.getEntry("pitchError")
    val rpmMaxErrorEntry = table.getEntry("rpm maxError")
    val pitchMaxErrorEntry = table.getEntry("pitchMaxError")
    val rpmErrorEntry = table.getEntry("RPM Error")
    val frontRPMOffsetEntry = table.getEntry("Front RPM Offset")
    val backRPMOffsetEntry = table.getEntry("Back RPM Offset")
    val pitchEntry = table.getEntry("Pitch")
    val pitchSetpointEntry = table.getEntry("Pitch Setpoint")
    val shootModeEntry = table.getEntry("Shoot Mode")
    val frontPitchOffsetEntry = table.getEntry("Front Pitch Offset")
    val backPitchOffsetEntry = table.getEntry("Back Pitch Offset")
    val pitchGoodEntry = table.getEntry("pitchGood")
    val aimGoodEntry = table.getEntry("aimGood")
    val rpmGoodEntry = table.getEntry("rpmGood")
    val allGoodEntry = table.getEntry("allGood")
    val frontRPMCurveEntry = table.getEntry("frontRPMCurve")
    val backRPMCurveEntry = table.getEntry("backRPMCurve")
    val frontPitchCurveEntry = table.getEntry("frontPitchCurve")
    val backPitchCurveEntry = table.getEntry("backPitchCurve")
    val distanceEntry = table.getEntry("fixedDistances")
    val colorAlignedEntry = table.getEntry("colorAligned")
    private val knownShotChooser = SendableChooser<String?>().apply {
        setDefaultOption("NOTSET", "notset")
        addOption("FENDER", "fender")
        addOption("WALL", "wall")
        addOption("SAFE", "safe")
    }


    val filter = LinearFilter.movingAverage(3) //if (tuningMode) {10} else {2})

    const val PITCH_LOW = -31.0
    const val PITCH_HIGH = 35.0

    const val PROXIMITY_STAGED_MIN = 200.0
    const val PROXMITY_STAGED_MAX_SAFE = 350.0

    var allGood = false
    var aimGood = false
    var rpmGood = false
    var pitchGood = false
    var isCargoAlignedWithAlliance = true
    var pitchOffset = if (isCompBot) 1.3 else - 76.0
    var curvepitchOffset = 0.0 //3.0
    var pitch: Double = 0.0
        get() = (pitchEncoder.absolutePosition - 0.218) * 33.0 / 0.182 + pitchOffset
        set(value) {
            pitchSetpoint = value
            field = value
        }
    var pitchSetpoint = pitch
        get() {
            if (tuningMode) {
                field = pitchSetpointEntry.getDouble(10.0)
            } else if (isKnownShot != knownShotType.NOTSET) {
                field = when (isKnownShot) {
                    knownShotType.FENDER -> 15.0
                    knownShotType.SAFE -> 35.0
                    knownShotType.WALL -> 35.0
                    else -> 15.0
                }
            } else if (!Limelight.useFrontLimelight && Limelight.hasValidBackTarget) {
                val tempPitch = backPitchCurve.getValue(Limelight.distance.asFeet)
                pitchSetpointEntry.setDouble(tempPitch)
                field = tempPitch
            } else if (Limelight.useFrontLimelight && Limelight.hasValidFrontTarget) {
                val tempPitch = frontPitchCurve.getValue(Limelight.distance.asFeet)
                pitchSetpointEntry.setDouble(tempPitch)
                field = tempPitch
            } else {
                field = pitchSetpointEntry.getDouble(10.0)
            }
            // don't allow values outside of range even with offset
            field = field.coerceIn(PITCH_LOW-curvepitchOffset, PITCH_HIGH-curvepitchOffset)
//            println("tuningMode $tuningMode     useFrontLL ${Limelight.useFrontLimelight}     frontTarget ${Limelight.hasValidFrontTarget}        backTarget ${Limelight.hasValidBackTarget}")
            return field + curvepitchOffset
        }
        set(value) {
            field = value.coerceIn(PITCH_LOW, PITCH_HIGH)
            pitchSetpointEntry.setDouble(field)
        }

    var pitchPDEnable = true
    val pitchPDController = PDController(0.05, 0.095) //0.06, 0.095) //0.055, 0.03) //0.06, 0.0) // d 0.1
    const val K_PITCH_FEED_FORWARD = -0.22
    val pitchIsReady : Boolean
        get() {
//            println("${pitchPDEnable}     ${pitchSetpoint > PITCH_LOW}     ${pitchSetpoint < PITCH_HIGH}     ${pitchEncoder.isConnected}")
            return pitchPDEnable && pitch > (PITCH_LOW - 2.0) && pitchSetpoint < (PITCH_HIGH + 2.0) && pitchEncoder.isConnected
        }
    val backPitchCurve: MotionCurve = MotionCurve()
    val frontPitchCurve: MotionCurve = MotionCurve()
    val frontRPMCurve:MotionCurve = MotionCurve()
    val backRPMCurve: MotionCurve = MotionCurve()

//    val facingCenter : Boolean
//        get() {
//            if (Drive.)
//        }

    var rpmSetpoint: Double = 0.0
        get() {
            if (tuningMode) {
                field = rpmSetpointEntry.getDouble(5000.0)
            } else if (isKnownShot != knownShotType.NOTSET) {
                field = when (isKnownShot) {
                    knownShotType.FENDER -> 3200.0
                    knownShotType.SAFE -> 4200.0
                    knownShotType.WALL -> 3450.0
                    else -> 3200.0
                }
                rpmSetpointEntry.setDouble(field)
            } else if (!Limelight.useFrontLimelight && Limelight.hasValidBackTarget) {
                field = backRPMCurve.getValue(Limelight.distance.asFeet) * backLLRPMOffset
                rpmSetpointEntry.setDouble(field)
            } else if (Limelight.useFrontLimelight && Limelight.hasValidFrontTarget) {
                field = frontRPMCurve.getValue(Limelight.distance.asFeet) * frontLLRPMOffset
                rpmSetpointEntry.setDouble(field)
            } else {
                field = rpmSetpointEntry.getDouble(5000.0)
            }
            return field
        }
    var rpm: Double
        get() = shootingMotor.velocity
        set(value) {
            shootingMotor.setVelocitySetpoint(value)
        }
    var rpmError = rpm
        get() = rpmErrorEntry.getDouble(rpmSetpoint - rpm)

    var shootMode = false



    const val RED = 'r'
    const val BLUE = 'b'
    const val NOTSET = 'n'
    val dsAllianceColor : Char
        get() = when (DriverStation.getAlliance().name.first().lowercaseChar()) {
            'r' -> RED
            'b' -> BLUE
            else -> NOTSET
        }
    var allianceColor = dsAllianceColor
    var stagedColorString = "notset"

    init {
        SmartDashboard.putData("KnownShot", knownShotChooser)

        backPitchCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        frontPitchCurve.setMarkBeginOrEndKeysToZeroSlope(false)

        backPitchCurve.storeValue(5.0, -8.0)
        backPitchCurve.storeValue(10.0, -19.0)
        backPitchCurve.storeValue(15.0, -27.0)
        backPitchCurve.storeValue(20.0, -30.0)
        backPitchCurve.storeValue(25.0, -32.0) //lower than min

        frontPitchCurve.storeValue(5.0, 24.8)
        frontPitchCurve.storeValue(10.0, 31.8)
        frontPitchCurve.storeValue(15.0, 36.0) //higher than max
        frontPitchCurve.storeValue(20.0, 36.0) //higher than max

        backRPMCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        frontRPMCurve.setMarkBeginOrEndKeysToZeroSlope(false)

        backRPMCurve.storeValue(5.0, 2800.0)
        backRPMCurve.storeValue(10.0, 3300.0) //3200.0)
        backRPMCurve.storeValue(15.0, 4000.0)
        backRPMCurve.storeValue(20.0, 5200.0) // 5100.0)
        backRPMCurve.storeValue(25.0, 5650.0)

        frontRPMCurve.storeValue(5.0, 3200.0)
        frontRPMCurve.storeValue(10.0, 3600.0) //3900.0)
        frontRPMCurve.storeValue(15.0, 4550.0)
        frontRPMCurve.storeValue(20.0, 5800.0)

        shootingMotor.config {
            followersInverted(true)
            coastMode()
            feedbackCoefficient = 1.0 / 2048.0 * 60.0
            pid {
                p(7.5e-6)
                i(0.0)
                d(2.0e-4)
                f(0.0140)
            }
        }

        pitchMotor.config {
            currentLimit(10, 15, 10)
            inverted(!isCompBot)
            brakeMode()
        }

        rpmSetpointEntry.setDouble(rpmSetpoint)
        pitchSetpointEntry.setDouble(pitch)

        GlobalScope.launch(MeanlibDispatcher) {
            var upPressed = false
            var downPressed = false
            var leftPressed = false
            var rightPressed = false
            frontRPMOffsetEntry.setDouble(frontLLRPMOffset)
            backRPMOffsetEntry.setDouble(backLLRPMOffset)
            distanceEntry.setDoubleArray(doubleArrayOf(5.0, 10.0, 15.0, 20.0))
            frontPitchCurveEntry.setDoubleArray(doubleArrayOf(frontPitchCurve.getValue(5.0), frontPitchCurve.getValue(10.0), frontPitchCurve.getValue(15.0), frontPitchCurve.getValue(20.0)))
            backPitchCurveEntry.setDoubleArray(doubleArrayOf(backPitchCurve.getValue(5.0), backPitchCurve.getValue(10.0), backPitchCurve.getValue(15.0), backPitchCurve.getValue(20.0)))
            frontRPMCurveEntry.setDoubleArray(doubleArrayOf(frontRPMCurve.getValue(5.0), frontRPMCurve.getValue(10.0), frontRPMCurve.getValue(15.0), frontRPMCurve.getValue(20.0)))
            backRPMCurveEntry.setDoubleArray(doubleArrayOf(backRPMCurve.getValue(5.0), backRPMCurve.getValue(10.0), backRPMCurve.getValue(15.0), backRPMCurve.getValue(20.0)))


            frontRPMOffsetEntry.setPersistent()
            backRPMOffsetEntry.setPersistent()
            frontPitchOffsetEntry.setPersistent()
            backPitchOffsetEntry.setPersistent()
            rpmSetpointEntry.setPersistent()

            pitchSetpoint = pitch
            filter.calculate(pitch)
            periodic {
                if (pitchIsReady && pitchPDEnable) {
                    val power = pitchPDController.update(filter.calculate(pitchSetpoint) - pitch) + (pitch + 30.0) / 60.0 * (-0.05) + 0.1 // mapping (-30.0, 30.0) to (0.1, 0.05)
                    pitchSetPower(power)
//                    println("pitchPower $power")
                }
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)
                shootModeEntry.setBoolean(shootMode)
                pitchMaxErrorEntry.setDouble(pitchMaxError)
                rpmMaxErrorEntry.setDouble(rpmMaxError)
                pitchErrorEntry.setDouble(pitchSetpoint-pitch)
                aimMaxErrorEntry.setDouble(aimMaxError)

                val filteredError = rpmErrorFilter.calculate(rpmError)
                aimGood = Limelight.aimError.absoluteValue < aimMaxError
                rpmGood = filteredError < rpmMaxError
                pitchGood = pitchSetpoint - pitch < pitchMaxError
                isCargoAlignedWithAlliance = (allianceColor == cargoColor || cargoColor == NOTSET)
                allGood = shootMode && aimGood && rpmGood && pitchGood

                aimGoodEntry.setBoolean(aimGood)
                rpmGoodEntry.setBoolean(rpmGood)
                pitchGoodEntry.setBoolean(pitchGood)
                allGoodEntry.setBoolean(allGood)
                colorAlignedEntry.setBoolean(isCargoAlignedWithAlliance)

                if (allGood) {
                    OI.driverController.rumble = 0.5
                } else {
                    OI.driverController.rumble = 0.0
                }

                 //val detectedColor: Color = m_colorSensor.color

//                println("Color = ${detectedColor.red} ${detectedColor.green} ${detectedColor.blue}")

                if (OI.operatorController.dPad == Controller.Direction.UP) {
                    upPressed = true
                } else if (OI.operatorController.dPad == Controller.Direction.DOWN) {
                    downPressed = true
                }
                if (OI.operatorController.dPad != Controller.Direction.UP && upPressed) {
                    upPressed = false
                    backLLRPMOffset += 0.01
                    println("up. hi.")
                }
                if (OI.operatorController.dPad != Controller.Direction.DOWN && downPressed) {
                    downPressed = false
                    backLLRPMOffset -= 0.01
                    println("down. hi.")
                }
                pitchEntry.setDouble(pitch)

                if (OI.operatorController.dPad == Controller.Direction.LEFT) {
                    leftPressed = true
                } else if (OI.operatorController.dPad == Controller.Direction.RIGHT) {
                    rightPressed = true
                }
                if (OI.operatorController.dPad != Controller.Direction.LEFT && leftPressed) {
                    leftPressed = false
                    frontLLRPMOffset -= 0.01
                    println("left. hi.")
                }
                if (OI.operatorController.dPad != Controller.Direction.RIGHT && rightPressed) {
                    rightPressed = false
                    frontLLRPMOffset += 0.01
                    println("right. hi.")
                }

                // adjust shot for non alliance color cargo
                stagedColorString = when (cargoColor) {
                    RED -> "red"
                    BLUE -> "blue"
                    else -> "notset"
                }
                val rpmBadShotAdjustment = 1.0 //if (isCargoAlignedWithAlliance) 1.0 else if (pitch > 0) 0.4 else 0.1
                stagedColorString = "$stagedColorString $isCargoAlignedWithAlliance ${colorSensor.proximity}"
                colorEntry.setString(stagedColorString)
                if (rpmBadShotAdjustment < 1.0) {
                    println("intentional bad shot for $stagedColorString ${DriverStation.getAlliance()}")
                }
                // set rpm for shot
                rpm = if (shootMode || tuningMode) rpmSetpoint * rpmBadShotAdjustment else 0.0

            }
        }
    }

    override fun preEnable() {
        shootMode = false
        allianceColor = dsAllianceColor
    }

    val cargoStageProximity : Int
        get() = colorSensor.proximity
    val cargoIsStaged : Boolean
        get() = colorSensor.proximity > PROXIMITY_STAGED_MIN
    val cargoColor : Char
        get() =  if (colorSensor.proximity < 180) NOTSET else if (colorSensor.color.red >= colorSensor.color.blue) RED else BLUE

    fun pitchSetPower(power: Double) {
        pitchMotor.setPercentOutput(power)
    }

    suspend fun changeAngle(angle: Double) {
        println("shooter change angle from $pitch to $angle")
        val angleCurve = MotionCurve()
        angleCurve.storeValue(0.0, pitch)
        angleCurve.storeValue((pitch - angle).absoluteValue / 30.0, angle)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            pitch = angleCurve.getValue(t)
            if (t >= angleCurve.length) {
                stop()
            }
        }
    }

    var backLLRPMOffset: Double = 1.0
        get() = backRPMOffsetEntry.getDouble(1.0)
        set(value) {
            field = value
            backRPMOffsetEntry.setDouble(value)
        }

    var frontLLRPMOffset: Double = 1.0
        get() = frontRPMOffsetEntry.getDouble(1.0)
        set(value) {
            field = value
            frontRPMOffsetEntry.setDouble(value)
        }

    var pitchDriverOffset: Double
        get() = if (Limelight.useFrontLimelight) frontLLPitchOffset else backLLPitchOffset
        set(value) {
            if (Limelight.useFrontLimelight) {
                frontLLPitchOffset = value
                println("set frontLL to $value")
            } else {
                backLLPitchOffset = value
                println("set backLL to $value")
            }
        }

    fun changePitchDriverOffset(change: Double) {
        pitchDriverOffset = pitchDriverOffset + change
        println("setting pitchOffset to ${pitchDriverOffset}")
    }

    var backLLPitchOffset: Double = 0.0
        get() = backPitchOffsetEntry.getDouble(0.0)
        set(value) {
            field = value
            backPitchOffsetEntry.setDouble(value)
        }
    var frontLLPitchOffset: Double = 0.0
        get() = frontPitchOffsetEntry.getDouble(0.0)
        set(value) {
            field = value
            frontPitchOffsetEntry.setDouble(value)
        }

    override suspend fun default() {
        periodic {
            pitchEntry.setDouble(pitch)
        }
    }
}

suspend fun Shooter.pitchPowerTest() = use(this) {
    var power = 0.0
    var upPressed = false
    var downPressed = false
    periodic {
        if (OI.driverController.dPad == Controller.Direction.UP) {
            upPressed = true
        } else if (OI.driverController.dPad == Controller.Direction.DOWN) {
            downPressed = true
        }
        if (OI.driverController.dPad != Controller.Direction.UP && upPressed) {
            upPressed = false
            power += 0.01
            println("up power= ${power}")
        }
        if (OI.driverController.dPad != Controller.Direction.DOWN && downPressed) {
            downPressed = false
            power -= 0.01
            println("down power= ${power}")
        }
        pitchMotor.setPercentOutput(power)
//        println("power= ${power}")
    }
}

