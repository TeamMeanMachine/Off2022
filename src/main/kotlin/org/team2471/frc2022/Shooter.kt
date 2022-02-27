package org.team2471.frc2022

import com.revrobotics.ColorSensorV3
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.I2C
import edu.wpi.first.wpilibj.Timer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.gradle.utils.`is`
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.Angle
import org.team2471.frc.lib.units.asFeet
import org.team2471.frc.lib.units.degrees
import kotlin.math.absoluteValue


object Shooter : Subsystem("Shooter") {
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
    val rpmOffsetEntry = table.getEntry("RPM Offset")
    val pitchEntry = table.getEntry("pitch")
    val pitchSetpointEntry = table.getEntry("pitch Setpoint")

    const val PITCH_LOW = -31.0
    const val PITCH_HIGH = 33.0

    var pitch: Double = 0.0
        get() = (pitchEncoder.get() - 0.218) * 33.0 / 0.182 -76.0
        set(value) {
            pitchSetpoint = value
            field = value
        }
    var pitchSetpoint = pitch
        get() {
            if (FrontLimelight.hasValidTarget) {
                val pitch = pitchCurve.getValue(FrontLimelight.distance.asFeet)
                pitchSetpointEntry.setDouble(pitch)
                return pitch
            } else {
                return pitchSetpointEntry.getDouble(10.0)
            }
        }
        set(value) {
            field = value.coerceIn(PITCH_LOW, PITCH_HIGH)
            pitchSetpointEntry.setDouble(field)
        }

    var pitchPDEnable = true
    val pitchPDController = PDController(0.06, 0.0) // d 0.1
    val pitchIsReady : Boolean
        get() {
            return pitchPDEnable && pitch > PITCH_LOW && pitch < PITCH_HIGH && pitchEncoder.isConnected
        }
    val pitchCurve: MotionCurve = MotionCurve()
    val rpmCurve: MotionCurve = MotionCurve()

//    val facingCenter : Boolean
//        get() {
//            if (Drive.)
//        }

    var rpmSetpoint: Double = 0.0
        get() {
            if (FrontLimelight.hasValidTarget) {
                field = rpmCurve.getValue(FrontLimelight.distance.asFeet) + rpmOffset
                rpmSetpointEntry.setDouble(rpm)
            } else {
                field = rpmSetpointEntry.getDouble(7000.0)
            }
            return field
        }
    var rpm: Double
        get() = shootingMotor.velocity
        set(value) {
            shootingMotor.setVelocitySetpoint(value)
        }

    var shootMode = false

    var color = "blue"


    init {
//        pitchMotor.setBounds(2.50, 1.55, 1.50, 1.45, 0.50)
        pitchCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        //right up against: 12.5
        pitchCurve.storeValue(18.3, 30.0) //rpm 7000
        pitchCurve.storeValue(13.5, 20.7)
        pitchCurve.storeValue(9.0, 5.0)

        rpmCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        //right up against: 4000
        rpmCurve.storeValue(18.3, 4000.0) //rpm 7000
        rpmCurve.storeValue(13.5, 3000.0)
        rpmCurve.storeValue(9.0, 2000.0)

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
            inverted(true)
        }

        rpmSetpointEntry.setDouble(rpmSetpoint)
//        pitchSetpointEntry.setDouble(10.0)

        GlobalScope.launch(MeanlibDispatcher) {
            var upPressed = false
            var downPressed = false
            rpmOffset = rpmOffsetEntry.getDouble(1600.0)

            periodic {
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)

                 //val detectedColor: Color = m_colorSensor.color

//                println("Color = ${detectedColor.red} ${detectedColor.green} ${detectedColor.blue}")

                if (OI.operatorController.dPad == Controller.Direction.UP) {
                    upPressed = true
                } else if (OI.operatorController.dPad == Controller.Direction.DOWN) {
                    downPressed = true
                }
                if (OI.operatorController.dPad != Controller.Direction.UP && upPressed) {
                    upPressed = false
                    pitchSetpoint += 2
                    //incrementRpmOffset()
                    println("up. hi.")
                }
                if (OI.operatorController.dPad != Controller.Direction.DOWN && downPressed) {
                    downPressed = false
                    pitchSetpoint -= 2
                    //decrementRpmOffset()
                    println("down. hi.")
                }
                pitchEntry.setDouble(pitch)
                if (colorSensor.proximity < 200) {
                    color = "none " +  colorSensor.proximity
                } else {
                    if (colorSensor.color.red >= colorSensor.color.blue) {
                        color = "red" + colorSensor.proximity
                    } else {
                        color = "blue" + colorSensor.proximity
                    }
                }
                colorEntry.setString(color)
//                println("red: ${colorSensor.configureColorSensor()}          blue: ${colorSensor.blue}")
//                println("angle = ${pitchAngle.asDegrees}")

            }
        }
    }

    override fun preEnable() {
        GlobalScope.launch(MeanlibDispatcher) {
            pitchSetpoint = pitch
            periodic {
                if (pitchIsReady) {
                    val power = pitchPDController.update(pitchSetpoint - pitch)
                    pitchSetPower(power)
                }
            }
        }
    }

    val cargoIsStaged : Boolean
        get() = colorSensor.proximity > 200

    fun pitchSetPower(power: Double) {
        pitchMotor.setPercentOutput(power)
    }

    suspend fun changeAngle(angle: Double) {
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

    var rpmOffset: Double = 0.0 //400.0
        set(value) {
            field = value
            rpmOffsetEntry.setDouble(value)
        }

    fun incrementRpmOffset() {
        rpmOffset += 20.0
    }

    fun decrementRpmOffset() {
        rpmOffset -= 20.0
    }

    suspend fun resetPitchEncoder() = use(this) {
//        if (!pitchPDEnable) {
//            pitchSetPower(1.0)
//            var lastEncoderPosition = Intake.intakeMotor.position
//            var samePositionCounter = 0
//            periodic {
//                if ((lastEncoderPosition - Intake.intakeMotor.position).absoluteValue < 0.01) {
//                    samePositionCounter++
//                } else {
//                    samePositionCounter = 0
//                }
//                if (samePositionCounter > 10) {
//                    this.stop()
//                }
//                lastEncoderPosition = Intake.intakeMotor.position
//            }
//            pitchSetPower(0.0)
//            Intake.intakeMotor.position = 66.6
//            pitchPDEnable = true
//        }
    }

    var current = shootingMotor.current

    override suspend fun default() {
        periodic {
//            shootingMotor.stop()
            pitchEntry.setDouble(pitch)
            rpm = 0.0
        }
    }
}