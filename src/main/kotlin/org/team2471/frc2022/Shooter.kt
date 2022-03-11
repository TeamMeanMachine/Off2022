package org.team2471.frc2022

import com.revrobotics.ColorSensorV3
import edu.wpi.first.math.filter.LinearFilter
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DriverStation
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.I2C
import edu.wpi.first.wpilibj.Timer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.asFeet
import kotlin.math.absoluteValue


object Shooter : Subsystem("Shooter") {
    val tuningMode = false
    val shootingMotor = MotorController(FalconID(Falcons.SHOOTER), FalconID(Falcons.SHOOTER_TWO)) //private
    private val pitchMotor = MotorController(TalonID(Talons.PITCH))
    private val table = NetworkTableInstance.getDefault().getTable(name)
    val pitchEncoder = DutyCycleEncoder(DigitalSensors.SHOOTER_PITCH)

    private val i2cPort: I2C.Port = I2C.Port.kMXP
    private val colorSensor = ColorSensorV3(i2cPort)
    val colorEntry = table.getEntry("Color")

    val rpmEntry = table.getEntry("RPM")
    val rpmSetpointEntry = table.getEntry("RPM Setpoint")
    val rpmErrorEntry = table.getEntry("RPM Error")
    val frontRPMOffsetEntry = table.getEntry("Front RPM Offset")
    val backRPMOffsetEntry = table.getEntry("Back RPM Offset")
    val pitchEntry = table.getEntry("Pitch")
    val pitchSetpointEntry = table.getEntry("Pitch Setpoint")
    val shootModeEntry = table.getEntry("Shoot Mode")
    val frontPitchOffsetEntry = table.getEntry("Front Pitch Offset")
    val backPitchOffsetEntry = table.getEntry("Back Pitch Offset")

    val filter = LinearFilter.movingAverage(2)

    const val PITCH_LOW = -31.0
    const val PITCH_HIGH = 35.0

    var pitchOffset = if (isCompBot) 1.3 else - 76.0
    var curvepitchOffset = 3.0
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
            } else if (!Limelight.useFrontLimelight && Limelight.hasValidBackTarget) {
                val tempPitch = -backPitchCurve.getValue(Limelight.distance.asFeet + 3.0)
                pitchSetpointEntry.setDouble(tempPitch)
                field = tempPitch
            } else if (Limelight.useFrontLimelight && Limelight.hasValidFrontTarget) {
                val tempPitch = backPitchCurve.getValue(Limelight.distance.asFeet)
                pitchSetpointEntry.setDouble(tempPitch)
                field = tempPitch
            } else {
                field = pitchSetpointEntry.getDouble(10.0)
            }
//            println("tuningMode $tuningMode     useFrontLL ${Limelight.useFrontLimelight}     frontTarget ${Limelight.hasValidFrontTarget}        backTarget ${Limelight.hasValidBackTarget}")
            return field + curvepitchOffset
        }
        set(value) {
            field = value.coerceIn(PITCH_LOW, PITCH_HIGH)
            pitchSetpointEntry.setDouble(field)
        }

    var pitchPDEnable = true
    val pitchPDController = PDController(0.02, 0.0) //0.055, 0.03) //0.06, 0.0) // d 0.1
    val pitchIsReady : Boolean
        get() {
//            println("${pitchPDEnable}     ${pitchSetpoint > PITCH_LOW}     ${pitchSetpoint < PITCH_HIGH}     ${pitchEncoder.isConnected}")
            return pitchPDEnable && pitch > (PITCH_LOW - 2.0) && pitchSetpoint < (PITCH_HIGH + 2.0) && pitchEncoder.isConnected
        }
    val backPitchCurve: MotionCurve = MotionCurve()
    val frontPitchCurve: MotionCurve = MotionCurve()
    val rpmCurve: MotionCurve = MotionCurve()

//    val facingCenter : Boolean
//        get() {
//            if (Drive.)
//        }

    var rpmSetpoint: Double = 0.0
        get() {
            if (tuningMode) {
                field = rpmSetpointEntry.getDouble(5000.0)
            } else if (Limelight.hasValidTarget) {
                field = rpmCurve.getValue(Limelight.distance.asFeet) + rpmOffset
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
        frontRPMOffsetEntry.setPersistent()
        backRPMOffsetEntry.setPersistent()
        frontPitchOffsetEntry.setPersistent()
        backPitchOffsetEntry.setPersistent()
//        pitchMotor.setBounds(2.50, 1.55, 1.50, 1.45, 0.50)
        //right up against: 12.5
//        pitchCurve.storeValue(5.0, 23.0)
//        pitchCurve.storeValue(10.0, 32.0)
//        pitchCurve.storeValue(15.0, 35.0)
//        pitchCurve.storeValue(20.0, 35.0)

        // 03/05 tuned
        backPitchCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        backPitchCurve.storeValue(5.0, 12.0)
        backPitchCurve.storeValue(10.0, 20.0)
        backPitchCurve.storeValue(15.0, 31.0)
        backPitchCurve.storeValue(20.0, 31.0)



        rpmCurve.setMarkBeginOrEndKeysToZeroSlope(false)
//        rpmCurve.storeValue(5.0, 3500.0)
//        rpmCurve.storeValue(10.0, 3750.0)
//        rpmCurve.storeValue(15.0, 4500.0)
//        rpmCurve.storeValue(20.0, 5500.0)
        rpmCurve.storeValue(5.0, 3200.0)
        rpmCurve.storeValue(10.0, 3500.0)
        rpmCurve.storeValue(15.0, 4050.0)
        rpmCurve.storeValue(20.0, 5000.0)

        shootingMotor.config {
            followersInverted(true)
            coastMode()
            feedbackCoefficient = 1.0 / 2048.0 * 60.0
            pid {
                p(1.3e-7)
                i(0.0)//i(0.0)
                d(0.0)//d(1.5e-3) //1.5e-3  -- we tried 1.5e9 and 1.5e-9, no notable difference  // we printed values at the MotorController and the wrapper
                f(0.0149)
            }
        }

        pitchMotor.config {
            currentLimit(10, 15, 10)
            inverted(!isCompBot)
            brakeMode()
        }

        rpmSetpointEntry.setDouble(rpmSetpoint)
//        pitchSetpointEntry.setDouble(10.0)

        GlobalScope.launch(MeanlibDispatcher) {
            var upPressed = false
            var downPressed = false
            var leftPressed = false
            var rightPressed = false
            frontRPMOffsetEntry.setDouble(frontLLRPMOffset)
            backRPMOffsetEntry.setDouble(backLLRPMOffset)
            pitchSetpoint = pitch
            periodic {
                if (pitchIsReady) {
                    val power = pitchPDController.update(filter.calculate(pitchSetpoint) - pitch)
                    pitchSetPower(power)
//                    println("pitchPower $power")
                }
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)
                shootModeEntry.setBoolean(shootMode)

                if (shootMode && Limelight.aimError < 3.0 && rpmError < 500.0 && pitchSetpoint - pitch < 5.0) {
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
                    changeRPMOffset(20.0)
                    println("up. hi.")
                }
                if (OI.operatorController.dPad != Controller.Direction.DOWN && downPressed) {
                    downPressed = false
                    changeRPMOffset(-20.0)
                    println("down. hi.")
                }
                pitchEntry.setDouble(pitch)

                if (OI.operatorController.dPad == Controller.Direction.LEFT) {
                    leftPressed = true
                } else if (OI.operatorController.dPad == Controller.Direction.DOWN) {
                    rightPressed = true
                }
                if (OI.operatorController.dPad != Controller.Direction.LEFT && leftPressed) {
                    leftPressed = false
                    changePitchDriverOffset(-2.0)
                    println("left. hi.")
                }
                if (OI.operatorController.dPad != Controller.Direction.RIGHT && rightPressed) {
                    rightPressed = false
                    changePitchDriverOffset(2.0)
                    println("right. hi.")
                }

                // adjust shot for non alliance color cargo
                stagedColorString = when (cargoColor) {
                    RED -> "red"
                    BLUE -> "blue"
                    else -> "notset"
                }
                val isCargoAlignedWithAlliance = (allianceColor == cargoColor || cargoColor == NOTSET)
                val rpmBadShotAdjustment = if (isCargoAlignedWithAlliance) 1.0 else if (pitch > 0) 0.4 else 0.1
                stagedColorString = "$stagedColorString $isCargoAlignedWithAlliance ${colorSensor.proximity}"
                colorEntry.setString(stagedColorString)
                if (rpmBadShotAdjustment < 1.0) {
                    println("intentional bad shot")
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
        get() = colorSensor.proximity > 250
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

    var rpmOffset: Double
        get() = if (Limelight.useFrontLimelight) frontLLRPMOffset else backLLRPMOffset
        set(value) {
            if (Limelight.useFrontLimelight) {
                frontLLRPMOffset = value
                println("set frontLL to $value")
            } else {
                backLLRPMOffset = value
                println("set backLL to $value")
            }
        }

    fun changeRPMOffset(change: Double) {
        rpmOffset = rpmOffset + change
        println("setting rpmOffset to ${rpmOffset}")
    }

    var backLLRPMOffset: Double = 700.0
        get() = backRPMOffsetEntry.getDouble(700.0)
        set(value) {
            field = value
            backRPMOffsetEntry.setDouble(value)
        }
    var frontLLRPMOffset: Double = 1200.0
        get() = frontRPMOffsetEntry.getDouble(1200.0)
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

    var backLLPitchOffset: Double = 700.0
        get() = backPitchOffsetEntry.getDouble(700.0)
        set(value) {
            field = value
            backPitchOffsetEntry.setDouble(value)
        }
    var frontLLPitchOffset: Double = 1200.0
        get() = frontPitchOffsetEntry.getDouble(1200.0)
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